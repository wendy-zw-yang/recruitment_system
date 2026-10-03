<template>
  <el-result icon="warning" title="页面不存在" sub-title="请检查地址是否正确">
    <template #extra>
      <el-button type="primary" @click="$router.push('/home')">返回首页</el-button>
    </template>
  </el-result>
</template>
