/* 文件作用：保存本地合同附件、读取历史版本，并在事务回滚时清理本次新文件。 */
package com.bishe.crm.module.contract.storage;

import com.bishe.crm.common.api.ErrorCode;
import com.bishe.crm.common.exception.BizException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/** 文件只位于后端本机；下载必须先通过 Service 中的合同归属与附件权限检查。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractFileStorage {
    public static final long MAX_BYTES = 20 * 1024 * 1024L;
    private final ContractStorageProperties properties;

    /** 每次上传使用唯一文件名，保留历史版本，不覆盖用户原文件。 */
    public String store(Long contractId, String extension, InputStream content, long size) {
        if (contractId == null || contractId <= 0 || extension == null
            || !extension.matches("\\.(pdf|doc|docx)") || size <= 0 || size > MAX_BYTES) {
            throw new BizException(ErrorCode.CONTRACT_FILE_INVALID);
        }
        Path root = localRoot();
        Path directory = root.resolve("contract-" + contractId);
        Path file = directory.resolve(UUID.randomUUID().toString().replace("-", "") + extension);
        boolean created = false;
        try {
            // 读取范围受20MB业务上限约束，并核对实际长度，拒绝不完整的附件。
            byte[] bytes = content.readNBytes((int) size + 1);
            if (bytes.length != size) throw new BizException(ErrorCode.CONTRACT_FILE_INVALID);
            Files.createDirectories(directory);
            if (!directory.toRealPath().equals(root.toRealPath().resolve("contract-" + contractId))) {
                throw new IOException("Invalid contract storage directory");
            }
            // CREATE_NEW防止意外覆盖；只清理本次确实创建过的文件。
            try (var output = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                output.write(bytes);
            }
            return file.toAbsolutePath().normalize().toString();
        } catch (IOException exception) {
            if (created) {
                try { Files.deleteIfExists(file); }
                catch (IOException ignored) { log.error("本地附件上传失败后清理未完成 contractId={}", contractId); }
            }
            throw storageFailure("upload", exception);
        }
    }

    /** 核验具体历史版本是否存在，同时阻止跨合同或目录之外的文件访问。 */
    public boolean exists(String location, Long contractId) {
        return localFile(location, contractId) != null;
    }

    /** 权限检查后读取合同文件，核对长度并保留业务原文件名。 */
    public Resource download(String location, Long contractId, String originalName, long expectedSize) {
        if (expectedSize <= 0 || expectedSize > MAX_BYTES) throw new BizException(ErrorCode.NOT_FOUND);
        Path file = localFile(location, contractId);
        if (file == null) throw new BizException(ErrorCode.NOT_FOUND);
        try (InputStream content = Files.newInputStream(file)) {
            byte[] bytes = content.readNBytes((int) expectedSize + 1);
            if (bytes.length != expectedSize) throw new BizException(ErrorCode.FILE_STORAGE_UNAVAILABLE);
            return new ByteArrayResource(bytes) {
                /** 响应使用数据库记录的原始文件名，而不是本机随机文件名。 */
                @Override
                public String getFilename() { return originalName; }
            };
        } catch (IOException exception) {
            throw storageFailure("download", exception);
        }
    }

    /** 数据库事务回滚时仅补偿刚新增的附件，既有历史版本继续保留。 */
    public void deleteCreated(String location, Long contractId) {
        try {
            Path file = localFile(location, contractId);
            if (file != null) Files.deleteIfExists(file);
        } catch (IOException exception) {
            log.error("合同附件回滚清理失败 contractId={}；请检查该事务新增文件，不能删除历史版本", contractId);
        }
    }

    /** 兼容数据库已有本地路径，限定为本合同目录，拒绝路径逃逸及符号链接跨目录。 */
    public Path localFile(String location, Long contractId) {
        if (location == null || contractId == null || contractId <= 0) return null;
        try {
            Path root = localRoot();
            Path expected = root.resolve("contract-" + contractId);
            Path file = Path.of(location).toAbsolutePath().normalize();
            if (!expected.equals(file.getParent()) || !Files.isRegularFile(file)) return null;
            Path realDirectory = root.toRealPath().resolve("contract-" + contractId);
            if (!expected.toRealPath().equals(realDirectory)
                || !realDirectory.equals(file.toRealPath().getParent())) return null;
            return file;
        } catch (IOException | RuntimeException exception) { return null; }
    }

    /** 解析可配置的附件根目录，默认与此前本地存储位置一致。 */
    private Path localRoot() { return Path.of(properties.getRoot()).toAbsolutePath().normalize(); }

    /** 日志只记录动作及异常类型，响应不泄露本机路径或内部堆栈。 */
    private BizException storageFailure(String action, Exception exception) {
        log.warn("本地合同附件操作失败 action={} type={}", action, exception.getClass().getSimpleName());
        return new BizException(ErrorCode.FILE_STORAGE_UNAVAILABLE);
    }
}
