<!-- 文件作用：商机详情当前内容下方的操作区，复用原权限及业务事件。 -->
<script setup>
import { Plus, Edit, UserFilled } from '@element-plus/icons-vue';
defineProps({ page: String, detail: Object, active: Boolean, selectedCollaborator: Object, pendingStageFollow: Object });
const emit = defineEmits(['navigate', 'dialog', 'create-contract', 'view-contract', 'remove-collaborator', 'review-follow']);
</script>
<template>
<div class="detail-action-bar" aria-label="当前详情页操作">
          <template v-if="page === 'stage'">
            <el-button @click="emit('navigate', 'follow')">跟进与核验</el-button><el-button @click="emit('navigate', 'evidence')">报价与合作确认</el-button>
          </template>
          <template v-if="page === 'overview'">
            <el-button v-if="detail.canEdit && active" :icon="Edit" @click="emit('dialog', 'edit')">编辑业务资料</el-button>
            <el-button v-if="detail.canManageCollaborators && active" :icon="UserFilled" @click="emit('dialog', 'collaborator')">添加协作人</el-button>
            <el-button v-if="detail.canManageCollaborators && active" type="danger" plain :disabled="!selectedCollaborator" @click="emit('remove-collaborator', selectedCollaborator)">移除所选协作人</el-button>
          </template>
          <template v-if="page === 'evidence'">
            <el-button v-if="detail.canEdit && active && detail.stageCode !== 'NEW'" data-task="QUOTE" type="primary" @click="emit('dialog', 'QUOTE')">{{ detail.quoteAt ? '更新报价' : '录入报价' }}</el-button>
            <el-button v-if="detail.canEdit && active && ['QUOTED','DEAL'].includes(detail.stageCode)" data-task="CUSTOMER_CONFIRMATION" type="primary" plain @click="emit('dialog', 'CONFIRMATION')">{{ detail.customerConfirmAt ? '更新合作确认' : '登记合作确认' }}</el-button>
            <el-button v-if="detail.canEdit && detail.stageCode === 'DEAL'" @click="emit('create-contract')">创建合同登记</el-button>
            <el-button v-if="['DEAL','WON'].includes(detail.stageCode)" @click="emit('view-contract')">合同与回款</el-button>
          </template>
          <template v-if="page === 'follow'">
            <el-button v-if="active" data-task="follow" type="primary" :icon="Plus" @click="emit('dialog', 'follow')">新增跟进</el-button>
            <el-button v-if="pendingStageFollow" data-task="review-follow" type="success" @click="emit('review-follow', pendingStageFollow)">核验本阶段跟进</el-button>
          </template>
          <template v-if="page === 'records' && active">
            <el-button v-if="detail.canSetThreshold" @click="emit('dialog', 'threshold')">调整停滞阈值</el-button>
            <el-button v-if="detail.canChangeStage" @click="emit('dialog', 'stage')">管理员修正阶段</el-button>
            <el-button v-if="detail.canDecidePending" type="warning" @click="emit('dialog', 'decision')">待关闭裁决</el-button>
          </template>
          <span class="scope-note">仅显示当前账号可办理的操作</span>
        </div>
</template>
<style scoped>
/* 与上方主要内容留出32px，操作区随内容排列。 */
.detail-action-bar { display:flex; gap:12px; flex-wrap:wrap; align-items:center; margin-top:32px; padding:0 0 4px; }
.detail-action-bar .el-button { margin:0; }
.scope-note { font-size:12px; color:var(--crm-text-secondary); }
</style>
