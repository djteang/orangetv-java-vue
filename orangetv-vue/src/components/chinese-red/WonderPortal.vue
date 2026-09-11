<script setup lang="ts">
defineProps<{ cosmic?: boolean }>()
</script>

<template>
  <svg viewBox="0 0 400 400" class="wonder-portal" :class="{ cosmic }" focusable="false">
    <g stroke="var(--portal-ink)" stroke-width="4.5" stroke-linecap="round" stroke-linejoin="round">
      <path fill="var(--portal-halo)" stroke="none" d="M197 14c37-5 43 16 69 18 32 3 45 14 55 35 10 22 44 21 45 55 2 24 30 41 16 68-12 23 11 51-10 73-20 21-11 46-43 58-26 10-31 38-68 34-30-3-43 26-73 14-34-14-63 4-83-20-17-21-48-13-56-44-7-27-38-38-26-69 9-23-14-49 1-74 20-30 7-57 35-72 33-18 31-49 66-51 34-2 43-26 72-25Z" />
      <g class="portal-orbit">
        <path fill="var(--portal-rim)" d="M205 39c27-4 45 20 67 23 38 5 33 35 52 53 25 24 13 43 24 67 17 37-10 52-14 79-5 27-37 34-53 54-22 28-51 11-76 22-28 12-52-12-77-14-27-3-29-31-51-45-26-16-14-41-26-63-13-27 7-43 6-70-1-30 29-38 38-60 12-28 40-22 62-36 18-12 31-7 48-10Z" />
        <path fill="var(--portal-light)" d="M195 63c46-2 79 30 103 55 33 34 38 80 22 114-11 24-20 66-67 72-28 4-57 28-91 10-24-13-51-14-57-45-7-35-40-48-28-86 13-40 2-60 38-87 24-18 50-32 80-33Z" />
        <path fill="var(--portal-deep)" d="M137 101c56-44 119-4 132 24 25 54-5 62-5 98 0 45-50 66-88 42-28-17-25-52-3-65 28-17 49 9 40 25 36-6 41-50 5-70-48-28-91 6-95 43-37-18-23-69 14-97Z" />
        <path fill="var(--portal-rim)" stroke="none" d="M155 140c41-34 91 13 76 41-17-37-47-27-60-12-21 26-12 61 15 80-44-3-69-76-31-109Z" />
        <path fill="none" stroke="var(--portal-spark)" stroke-width="9" d="M180 81c44-13 83 22 88 41M92 187c-8 26 6 51 18 59m69 42c19 8 48 3 66-10m56-98c8 16 2 35-3 44" />
        <path fill="none" stroke-width="3" d="m125 70 10 7m160 34-10 7M319 250l-11-2M122 304l7-9M63 167l11 3" />
      </g>
      <g class="portal-motes" fill="var(--portal-light)">
        <path d="m48 74 4 12 12 4-12 4-4 12-4-12-12-4 12-4Z" />
        <path d="m340 300 4 10 10 4-10 4-4 10-4-10-10-4 10-4Z" />
        <ellipse cx="347" cy="93" rx="7" ry="5" transform="rotate(26 347 93)" />
        <ellipse cx="82" cy="329" rx="5" ry="8" transform="rotate(35 82 329)" />
        <circle cx="202" cy="19" r="4" />
        <circle cx="26" cy="233" r="4" />
      </g>
    </g>
  </svg>
</template>

<style scoped>
.wonder-portal { --portal-ink: #375246; --portal-halo: #e9efb3; --portal-rim: #a5c85c; --portal-light: #e3f598; --portal-deep: #68b58a; --portal-spark: #f6ffd1; }
.wonder-portal.cosmic { --portal-ink: #4b4566; --portal-halo: #d7cee8; --portal-rim: #a99acb; --portal-light: #c6e798; --portal-deep: #789e9f; --portal-spark: #efffc8; }
.portal-orbit { transform-origin: 200px 200px; animation: portal-orbit 38s linear infinite; }
.portal-motes { animation: portal-motes 6s ease-in-out infinite; }
.portal-orbit, .portal-motes { animation-play-state: var(--china-animation-play-state, running); }
@keyframes portal-orbit { to { transform: rotate(360deg); } }
@keyframes portal-motes { 0%, 100% { opacity: .65; } 50% { opacity: 1; } }
</style>
