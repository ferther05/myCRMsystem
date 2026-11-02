/* 文件作用：验证本地合同文件版本保留、受控路径、长度检查和回滚清理。 */
package com.bishe.crm.module.contract.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bishe.crm.common.api.ErrorCode;
import com.bishe.crm.common.exception.BizException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 使用隔离临时目录，不读写用户已有合同文件。 */
class ContractFileStorageTest {
    Path root;
    ContractFileStorage storage;

    /** 在 Maven 构建目录下创建独立测试空间，避开受限的系统临时目录。 */
    @BeforeEach
    void setup() throws Exception {
        root = Path.of("target", "test-files", UUID.randomUUID().toString()).toAbsolutePath();
        Files.createDirectories(root);
        var settings = new ContractStorageProperties();
        settings.setRoot(root.toString());
        storage = new ContractFileStorage(settings);
    }

    /** 只清理由本测试 UUID 隔离目录创建的测试文件。 */
    @AfterEach
    void cleanup() throws Exception {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    /** 同名业务文件反复上传会产生独立版本，下载保持原名与原内容。 */
    @Test
    void versionsStaySeparateAndDownloadKeepsContentAndName() throws Exception {
        String first = store(12L, "first");
        String second = store(12L, "second");
        assertThat(first).isNotEqualTo(second);
        assertThat(Path.of(first).getParent()).isEqualTo(root.resolve("contract-12"));
        assertThat(Files.readString(Path.of(first))).isEqualTo("first");
        var downloaded = storage.download(second, 12L, "已签署合同.pdf", 6);
        assertThat(downloaded.getFilename()).isEqualTo("已签署合同.pdf");
        assertThat(downloaded.getContentAsByteArray()).isEqualTo("second".getBytes(StandardCharsets.UTF_8));
    }

    /** 数据库旧路径及旧文件名继续可读，不需要迁移历史附件。 */
    @Test
    void existingLocalHistoryRemainsReadable() throws Exception {
        Path legacy = root.resolve("contract-12/legacy-original-name.pdf");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "old");
        assertThat(storage.exists(legacy.toString(), 12L)).isTrue();
        assertThat(storage.download(legacy.toString(), 12L, "旧合同.pdf", 3).getContentAsByteArray())
            .isEqualTo("old".getBytes(StandardCharsets.UTF_8));
    }

    /** 补偿只删除本次上传版本，重复清理安全且不删除既有版本。 */
    @Test
    void rollbackDeletesOnlyTheCreatedVersion() throws Exception {
        String old = store(12L, "old");
        String created = store(12L, "new");
        storage.deleteCreated(created, 12L);
        storage.deleteCreated(created, 12L);
        assertThat(Files.exists(Path.of(created))).isFalse();
        assertThat(Files.readString(Path.of(old))).isEqualTo("old");
    }

    /** 不能用其他合同ID或根目录外路径读写当前附件。 */
    @Test
    void rejectsCrossContractAndOutsideRootPaths() throws Exception {
        String location = store(12L, "private");
        assertThat(storage.exists(location, 13L)).isFalse();
        storage.deleteCreated(location, 13L);
        assertThat(Files.exists(Path.of(location))).isTrue();
        Path outside = root.resolve("unrelated.pdf");
        Files.writeString(outside, "private");
        assertThat(storage.exists(outside.toString(), 12L)).isFalse();
        assertThatThrownBy(() -> storage.download(location, 13L, "合同.pdf", 7))
            .isInstanceOf(BizException.class).extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
    }

    /** 类型、大小或实际上传长度不合法时拒绝保存，不留下半个附件。 */
    @Test
    void rejectsInvalidUploadsWithoutLeavingFiles() throws Exception {
        assertThatThrownBy(() -> storage.store(12L, ".exe", new ByteArrayInputStream(new byte[1]), 1))
            .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> storage.store(12L, ".pdf", new ByteArrayInputStream(new byte[0]),
            ContractFileStorage.MAX_BYTES + 1)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> storage.store(12L, ".pdf", new ByteArrayInputStream(new byte[3]), 2))
            .isInstanceOf(BizException.class);
        try (var files = Files.walk(root)) {
            assertThat(files.filter(Files::isRegularFile).count()).isZero();
        }
    }

    /** 缺失或长度已改变的附件不能冒充正常下载成功。 */
    @Test
    void missingOrChangedFilesFailClearly() {
        String location = store(12L, "actual");
        assertThatThrownBy(() -> storage.download(location, 12L, "合同.pdf", 2))
            .isInstanceOf(BizException.class).extracting("errorCode")
            .isEqualTo(ErrorCode.FILE_STORAGE_UNAVAILABLE);
        storage.deleteCreated(location, 12L);
        assertThatThrownBy(() -> storage.download(location, 12L, "合同.pdf", 6))
            .isInstanceOf(BizException.class).extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
    }

    /** 用短文本代替真实合同内容，构造独立版本。 */
    private String store(Long contractId, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return storage.store(contractId, ".pdf", new ByteArrayInputStream(bytes), bytes.length);
    }
}
