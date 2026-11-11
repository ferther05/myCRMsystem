<!-- 文件作用：管理或查询团队账号、角色、启停状态和密码重置。 -->
<script setup>
import DetailSection from '@/components/DetailSection.vue';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElDescriptions, ElDescriptionsItem, ElDialog, ElSelect, ElOption, ElMessage } from 'element-plus';
import PageHeading from '@/components/PageHeading.vue';
import DataTable from '@/components/DataTable.vue';
import { useAuthStore } from '@/store/auth';
import { ROLE_NAMES } from '@/utils/auth-rules';
import {
  ACCOUNT_STATUS,
  check,
  validUsername,
  validPhone,
  validInitialPassword,
} from '@/utils/account-rules';
import * as api from '@/api/users';

const auth = useAuthStore();
const router = useRouter();
const admin = computed(/* 判断当前身份是否为管理员。 */ () => auth.user?.roleCode === 'ADMIN');
const rows = ref([]);
const selectedAccount = ref(null);
const teams = ref([]);
const total = ref(0);
const page = ref(1);
const size = ref(10);
const keyword = ref('');
const status = ref('');
const loading = ref(false);
const failure = ref('');
const dialog = ref('');
const dialogOpen = computed({
  /* 将弹窗模式转换为是否打开。 */ get: () => Boolean(dialog.value),
  /* 关闭弹窗时清理操作模式。 */ set: (value) => {
    if (!value) dialog.value = '';
  },
});
const target = ref(null);
const saving = ref(false);
const formError = ref('');
const formRef = ref();
const form = reactive({
  username: '',
  realName: '',
  phone: '',
  email: '',
  roleCode: 'SPECIALIST',
  teamId: null,
  accountStatus: 'ACTIVE',
  initialPassword: '',
});
const titles = { create: '直接创建账号', access: '调整角色与账号状态', reset: '重置登录密码' };
const rules = {
  username: [
    {
      validator: check(
        validUsername,
        '账号为 3～64 位字母、数字、点、横线或下划线，以字母或数字开头',
      ),
      trigger: 'blur',
    },
  ],
  realName: [
    {
      required: true,
      whitespace: true,
      max: 80,
      message: '请输入姓名，最多 80 字',
      trigger: 'blur',
    },
  ],
  phone: [{ validator: check(validPhone, '联系电话格式不正确'), trigger: 'blur' }],
  email: [{ type: 'email', max: 160, message: '请输入有效邮箱，最多 160 字', trigger: 'blur' }],
  initialPassword: [
    {
      validator: check(validInitialPassword, '密码需 8～64 位，包含字母和数字，不能含空格或中文'),
      trigger: 'blur',
    },
  ],
  teamId: [
    {
      /* 校验当前字段并通过回调返回结果。 */ validator: (_r, value, callback) =>
        form.roleCode === 'ADMIN' || value ? callback() : callback(new Error('请选择所属团队')),
      trigger: 'change',
    },
  ],
};
watch(
  /* 读取需要监听的响应式字段。 */ () => form.roleCode,
  /* 响应数据变化，更新关联页面状态。 */ (role) => {
    if (role === 'ADMIN') form.teamId = null;
  },
);
/** 加载当前页面所需的数据并维护加载和错误状态。 */
async function load() {
  loading.value = true;
  failure.value = '';
  try {
    const result = await api.listUsers({
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim() || undefined,
      accountStatus: status.value || undefined,
    });
    rows.value = result.list;
    total.value = result.total;
  } catch (error) {
    failure.value = error.message;
  } finally {
    loading.value = false;
  }
}
/** 回到第一页并按当前条件重新查询。 */
async function search() {
  page.value = 1;
  await load();
}
watch([page, size], load);
/** 初始化并打开当前业务操作弹窗。 */
async function open(kind, row = null) {
  formError.value = '';
  try {
    // 编辑前重新取得最新版本，因为登录也可能更新账号版本。
    target.value = row ? await api.getUser(row.id) : null;
    if (kind !== 'reset') teams.value = await api.listTeams();
    Object.assign(
      form,
      {
        username: '',
        realName: '',
        phone: '',
        email: '',
        roleCode: 'SPECIALIST',
        teamId: null,
        accountStatus: 'ACTIVE',
        initialPassword: '',
      },
      target.value || {},
    );
    dialog.value = kind;
  } catch (error) {
    ElMessage.error(error.message);
  }
}
/** 按管理员操作类型创建账号、调整权限或重置密码。 */
async function save() {
  if (!(await formRef.value.validate().catch(/* 将表单校验失败转换为未通过结果。 */ () => false)))
    return;
  saving.value = true;
  formError.value = '';
  try {
    if (dialog.value === 'create')
      await api.createUser({
        username: form.username,
        realName: form.realName.trim(),
        phone: form.phone || null,
        email: form.email || null,
        roleCode: form.roleCode,
        teamId: form.teamId,
        initialPassword: form.initialPassword,
      });
    else if (dialog.value === 'access')
      await api.changeAccess(target.value.id, {
        version: target.value.version,
        roleCode: form.roleCode,
        teamId: form.teamId,
        accountStatus: form.accountStatus,
      });
    else
      await api.resetPassword(target.value.id, {
        version: target.value.version,
        initialPassword: form.initialPassword,
      });
    dialog.value = '';
    form.initialPassword = '';
    ElMessage.success('操作成功；涉及权限或密码的调整会撤销该账号旧会话');
    await load();
  } catch (error) {
    formError.value = `${error.message}${error.status === 409 ? '；请关闭弹窗并刷新列表后重试。' : ''}`;
  } finally {
    saving.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div>
    <PageHeading
      :title="admin ? '账号与团队管理' : '团队成员'"
      :description="
        admin
          ? '管理员查看全部账号，管理角色、团队、启停状态和密码。'
          : '仅显示本团队成员；新成员账号需提交管理员审核。'
      "
    >
      <el-button v-if="admin" type="primary" @click="open('create')">创建账号</el-button>
      <el-button v-else type="primary" @click="router.push('/approvals')">申请新成员账号</el-button>
    </PageHeading>
    <form class="panel list-toolbar" @submit.prevent="search">
      <el-input
        v-model="keyword"
        placeholder="搜索账号或姓名"
        aria-label="搜索账号或姓名"
        maxlength="80"
        clearable
      />
      <el-select v-model="status" placeholder="全部状态" aria-label="账号状态" clearable>
        <el-option
          v-for="(label, value) in ACCOUNT_STATUS"
          :key="value"
          :value="value"
          :label="label"
        />
      </el-select>
      <el-button type="primary" native-type="submit" :loading="loading">查询</el-button>
      <el-button :disabled="loading" @click="load">刷新</el-button>
    </form>
    <DataTable
      :rows="rows"
      :total="total"
      :loading="loading"
      :error="failure"
      v-model:page="page"
      @retry="load"
    >
      <el-table-column prop="username" label="登录账号" min-width="165" />
      <el-table-column prop="realName" label="姓名" min-width="110" />
      <el-table-column label="角色" min-width="100">
        <template #default="{ row }">{{ ROLE_NAMES[row.roleCode] }}</template>
      </el-table-column>
      <el-table-column prop="teamName" label="所属团队" min-width="150">
        <template #default="{ row }">{{ row.teamName || '系统管理' }}</template>
      </el-table-column>
      <el-table-column label="状态" min-width="115">
        <template #default="{ row }">
          <el-tag
            :type="
              row.accountStatus === 'ACTIVE'
                ? 'success'
                : row.accountStatus === 'PENDING'
                  ? 'warning'
                  : 'info'
            "
          >
            {{ ACCOUNT_STATUS[row.accountStatus] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="95" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="selectedAccount = row">详情</el-button></template></el-table-column>
    </DataTable>
    <p class="scope-note">
      {{
        admin
          ? '管理员账号不绑定团队；经理和专员必须绑定启用的团队。有关联业务的账号需先完成交接，才能调整角色或团队。'
          : '团队范围由后端根据当前登录身份确定，修改网址或请求参数不能扩大范围。'
      }}
    </p>
<el-drawer :model-value="Boolean(selectedAccount)" @close="selectedAccount = null" title="账号详情" size="min(620px, 100%)"><template v-if="selectedAccount">
<DetailSection title="身份与归属" emphasis><el-descriptions :column="1" border><el-descriptions-item label="姓名 / 账号">{{ selectedAccount.realName }} / {{ selectedAccount.username }}</el-descriptions-item><el-descriptions-item label="角色">{{ ROLE_NAMES[selectedAccount.roleCode] }}</el-descriptions-item><el-descriptions-item label="所属团队">{{ selectedAccount.teamName || '系统管理' }}</el-descriptions-item><el-descriptions-item label="账号状态">{{ ACCOUNT_STATUS[selectedAccount.accountStatus] }}</el-descriptions-item></el-descriptions></DetailSection>
<DetailSection title="联系资料"><el-descriptions :column="1" border><el-descriptions-item label="联系电话">{{ selectedAccount.phone || '未填写' }}</el-descriptions-item><el-descriptions-item label="邮箱">{{ selectedAccount.email || '未填写' }}</el-descriptions-item></el-descriptions></DetailSection>
<DetailSection title="账号安全"><p>{{ selectedAccount.mustChangePassword ? '下次登录需修改初始密码' : '已完成密码设置' }}</p><div v-if="admin && selectedAccount.id !== auth.user.id && selectedAccount.accountStatus !== 'PENDING'" class="detail-actions"><el-button @click="open('access', selectedAccount); selectedAccount = null">角色与状态</el-button><el-button @click="open('reset', selectedAccount); selectedAccount = null">重置密码</el-button></div></DetailSection>
</template></el-drawer>
    <el-dialog
      v-model="dialogOpen"
      :title="titles[dialog]"
      width="560px"
      :close-on-click-modal="false"
      :close-on-press-escape="!saving"
      :show-close="!saving"
      @closed="
        form.initialPassword = '';
        formRef?.resetFields();
      "
    >
      <el-alert
        v-if="formError"
        :title="formError"
        type="error"
        :closable="false"
        show-icon
        class="form-error"
      />
      <p v-if="target">目标账号：{{ target.username }} · {{ target.realName }}</p>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        :disabled="saving"
        @submit.prevent="save"
      >
        <template v-if="dialog === 'create'">
          <el-form-item label="登录账号" prop="username">
            <el-input v-model="form.username" maxlength="64" autocomplete="off" />
          </el-form-item>
          <el-form-item label="姓名" prop="realName">
            <el-input v-model="form.realName" maxlength="80" />
          </el-form-item>
          <el-form-item label="联系电话" prop="phone">
            <el-input v-model="form.phone" maxlength="32" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="form.email" maxlength="160" />
          </el-form-item>
        </template>
        <template v-if="dialog !== 'reset'">
          <el-form-item label="角色" prop="roleCode">
            <el-select v-model="form.roleCode">
              <el-option
                v-for="(label, value) in ROLE_NAMES"
                :key="value"
                :label="label"
                :value="value"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.roleCode !== 'ADMIN'" label="所属团队" prop="teamId">
            <el-select v-model="form.teamId" placeholder="选择启用的团队">
              <el-option
                v-for="team in teams.filter(/* 仅提供启用的团队供账号选择。 */ (t) => t.enabled)"
                :key="team.id"
                :label="team.teamName"
                :value="team.id"
              />
            </el-select>
          </el-form-item>
        </template>
        <el-form-item v-if="dialog === 'access'" label="账号状态" prop="accountStatus">
          <el-select v-model="form.accountStatus">
            <el-option label="已启用" value="ACTIVE" />
            <el-option label="已停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="dialog !== 'access'" label="初始密码" prop="initialPassword">
          <el-input
            v-model="form.initialPassword"
            type="password"
            show-password
            maxlength="64"
            autocomplete="new-password"
          />
        </el-form-item>
        <p class="scope-note">
          {{
            dialog === 'access'
              ? '保存后该账号需重新登录；停用不自动释放业务归属。'
              : '请自行妥善告知使用人；系统只保存密码哈希，首次登录必须改密。'
          }}
        </p>
        <el-button type="primary" native-type="submit" :loading="saving">确认保存</el-button>
        <el-button :disabled="saving" @click="dialog = ''">取消</el-button>
      </el-form>
    </el-dialog>
  </div>
</template>
<style scoped>
.list-toolbar {
  display: flex;
  gap: 12px;
  padding: 18px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}
.list-toolbar .el-input {
  width: 260px;
}
.list-toolbar .el-select {
  width: 160px;
}
.scope-note {
  color: var(--crm-text-secondary);
  font-size: 12px;
  line-height: 1.8;
  margin-top: 16px;
}
:deep(.el-dialog) {
  max-width: calc(100vw - 24px);
}
:deep(.el-form .el-select) {
  width: 100%;
}
@media (max-width: 600px) {
  .list-toolbar .el-input {
    width: 100%;
  }
  .list-toolbar {
    padding: 14px;
    gap: 8px;
  }
}
</style>
