<script setup lang="ts">
import DiscoveryState from './DiscoveryState.vue'
defineProps<{ failed: boolean }>()
defineEmits<{ retry: [] }>()
</script>

<template>
  <DiscoveryState compact variant="anime" :mode="failed ? 'error' : 'empty'" :title="failed ? '新番信号暂时迷路了' : '今日暂无新番'" :description="failed ? '放送表暂时加载失败，稍后再连接一次吧。' : '今天的放送星球有点安静，先去发现其他好故事吧。'" :action-label="failed ? '重新加载' : undefined" @action="$emit('retry')" />
</template>
