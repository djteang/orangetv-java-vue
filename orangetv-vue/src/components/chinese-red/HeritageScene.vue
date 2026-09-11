<script setup lang="ts">
import { useId } from 'vue'

const sceneId = 'heritage-' + useId()
</script>

<template>
  <div class="scene-artwork heritage-art">
    <div class="heritage-window">
      <svg viewBox="0 0 420 420" focusable="false">
        <g fill="none" stroke="#c98461" stroke-linecap="round" stroke-linejoin="round">
          <path fill="#ffe0ab" stroke-width="5" d="M212 31c106 3 183 77 178 181-4 107-73 179-180 179-98 1-182-76-181-178C26 112 108 27 212 31Z" />
          <path stroke-width="11" stroke-dasharray="2 17" d="M212 46a165 165 0 1 1-2 0Z" />
          <path stroke-width="4" d="M210 65c85 0 149 59 148 145 0 88-58 145-148 147-88 2-150-61-147-147C63 124 126 64 210 65Z" />
          <path stroke-width="5" d="M123 99v62H78m218-63v63h47M124 323v-63H77m219 63v-63h47M159 76v71l-41 63 41 63v70m102-267v70l41 64-41 62v71M79 181h58m-58 59h58m148-59h58m-58 59h58" />
          <path fill="#f7c77e" stroke-width="4" d="m210 112 81 98-81 98-81-98Z" />
          <path stroke-width="4" d="m210 146 53 64-53 63-53-63Z" />
          <text x="210" y="235" text-anchor="middle" stroke="none" fill="#b76045" font-size="65" font-family="serif" font-weight="700">华</text>
        </g>
      </svg>
    </div>

    <div class="heritage-lanterns">
      <svg viewBox="0 0 360 530" focusable="false">
        <defs>
          <g :id="sceneId + '-lantern'" stroke="#603c36" stroke-width="4" stroke-linecap="round" stroke-linejoin="round">
            <path fill="none" d="M0-115V-64" />
            <path fill="#f1c45f" d="m-24-67 46 2 5 14-56 2Z" />
            <path fill="#e7644b" d="M-24-51c-79 16-87 102-40 141 33 24 86 21 115 2 52-35 50-121-28-143Z" />
            <path fill="#f08b5b" stroke="none" d="M-34-44c-37 18-54 65-29 98 8-47 24-67 43-80Z" />
            <path fill="#bd4539" stroke="none" d="M31-36c48 19 60 68 30 106-23 26-58 39-96 24 62-8 90-58 66-130Z" />
            <path fill="none" stroke="#873c32" stroke-width="3" d="M-11-46c-33 38-28 109-6 145M13-45c30 37 25 111 8 145M-43-40c-39 42-32 100-13 116m96-116c33 41 33 88 11 118" />
            <path fill="#f9cf73" d="m-39-43 77 2 6 9-87 1Zm9 134 58 1 8 11-72 2Z" />
            <path fill="#ffe4a0" stroke-width="3" d="m-1-9 27 39L0 69l-30-40Z" />
            <text x="-1" y="41" text-anchor="middle" stroke="none" fill="#a93630" font-size="30" font-family="serif" font-weight="700">福</text>
            <g class="lantern-tassel">
              <path fill="none" d="M0 104v20" />
              <path fill="#f8c760" d="m0 119 10 12-10 14-11-13Z" />
              <path fill="#df6647" d="m-8 141 16 1 12 47-38 1Z" />
              <path fill="none" stroke="#913c32" stroke-width="2.5" d="m-3 152-2 30m9-29 4 29" />
            </g>
          </g>
        </defs>
        <g transform="translate(228 166)"><g class="lantern-sway"><use :href="'#' + sceneId + '-lantern'" /></g></g>
        <g transform="translate(86 310) scale(.69)"><g class="lantern-sway lantern-sway-later"><use :href="'#' + sceneId + '-lantern'" /></g></g>
        <g fill="#d69a5a" stroke="#815544" stroke-width="2.5" class="heritage-sparks">
          <path d="m62 152 4 10 10 4-10 4-4 10-4-10-10-4 10-4Z" />
          <path d="m287 377 4 11 11 4-11 4-4 11-4-11-11-4 11-4Z" />
          <circle cx="316" cy="278" r="4" /><circle cx="147" cy="437" r="4" />
        </g>
      </svg>
    </div>

    <div class="heritage-town">
      <svg viewBox="0 0 1600 360" preserveAspectRatio="xMidYMax slice" focusable="false">
        <defs>
          <g :id="sceneId + '-pavilion'" stroke="#966b51" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
            <path fill="#f4d7a8" d="M-59 0v-86H59V0Z" />
            <path fill="#d99b70" d="M-85-76c31-7 29-28 85-43 55 15 54 36 85 43l-20 9H-67Z" />
            <path fill="#eec88d" d="M-47-109v-58h94v58Z" />
            <path fill="#cf875e" d="M-68-158c31-6 35-16 68-34 31 17 37 30 68 34l-12 10H-55Z" />
            <path fill="none" stroke="#754e40" d="M-39-156H37m-91 80H54M-31-148v31m32-31v28m30-28v31M-40-65v61M0-66V0m39-65v60" />
            <path fill="#fbe7bb" d="M-7-190v-15q8-13 15 0v15" />
          </g>
        </defs>
        <g fill="#e4b58b" stroke="#bd8866" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
          <path d="M-20 263c116-67 200 27 308-17 83-33 142 31 243 21 137-14 163 59 287 21 110-34 203 24 302-8 101-32 172 10 249-24 83-36 174-17 251 3v121H-20Z" />
          <path d="M-20 246v-99l19-15 32 15v56l49-17 54 15v59m1240-7V138l36-22 38 22v91l73-24 49 22v48m-1280-32v-78h97v79m926 13v-58h54v65" />
          <path fill="none" d="M15 161v29m7-29v29m229-11h27m-27 18h27m1112-47v30m14-30v30" />
        </g>
        <use :href="'#' + sceneId + '-pavilion'" transform="translate(162 286) scale(1.02)" />
        <use :href="'#' + sceneId + '-pavilion'" transform="translate(1387 300) scale(.91)" />
        <g fill="#f6dfb7" stroke="#bb8c69" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M-28 347c-19-45 30-73 66-45-9-61 64-88 98-39 32-43 96-19 94 21 46-15 65 11 73 36 42-12 87 6 88 38H-28Z" />
          <path fill="none" d="M26 339c-16-22 9-46 30-28m37 5c-19-30 25-64 46-32 16 24-24 37-26 13m55 18c4-28 37-27 49-7m42 37h58" />
          <path d="M1202 364c-16-39 25-61 56-41 4-40 41-48 69-25 2-51 55-71 83-37 28-25 67-7 68 23 41-20 85-8 92 38 40-25 76-1 75 42Z" />
          <path fill="none" d="M1291 341c-12-27 28-38 35-16m38-20c-6-31 39-39 43-9 2 16-25 23-22 6m63 36c14-18 33-15 40-2" />
        </g>
      </svg>
    </div>

    <div class="heritage-lion">
      <svg viewBox="0 0 400 440" focusable="false">
        <ellipse cx="191" cy="412" rx="114" ry="10" fill="#c1855d" opacity=".2" />
        <g class="lion-dance" stroke="#653b32" stroke-width="4.5" stroke-linecap="round" stroke-linejoin="round">
          <path fill="#d9593f" d="M139 264c-55 23-67 55-61 88l-13 29 14 7 21-7 14 16 19-13 32 12 19-18 32 15 28-21 29 8 11-25c-9-47-15-67-39-86Z" />
          <path fill="#f8c76f" d="M124 289c-10 40-27 48-13 83l20-11 23 19 18-13 24 7 18-26 36 13-16-59Z" />
          <path fill="#e47449" d="m88 367-10 26c-21-1-28 7-22 17l62-2 4-30m108-3 9 17c22-1 29 10 24 19l-64-2-5-31" />
          <path fill="none" stroke-width="3" d="m74 397 8 1m11-2 8 1m120 0 7 1m12-1 8 1" />
          <g class="lion-head">
            <path fill="#edaa55" d="M82 106 57 87l-19 10 5 40-12 19 26 35 40-18m203-62 27-23 20 12-7 36 16 20-28 39-36-26" />
            <path fill="#bd4939" d="M85 122 57 114l3 25-12 10 22 22m233-42 24-13-5 24 15 12-23 20" />
            <path fill="#f3c978" d="M93 85 82 62l29 3 8-23 31 12 25-28 24 21 30-17 17 27 29-2 7 25 34 6-4 25 24 16-15 31 17 26-19 27 2 27-31 6-10 32-31-6-28 21-30-16-30 14-24-25-28 2-3-32-25-14 13-29-18-26 14-22Z" />
            <path fill="#e76241" d="M101 106c6-43 45-38 91-38 50-4 93 1 105 40l-8 115c-32 43-141 44-184-2Z" />
            <path fill="#b94434" stroke="none" d="M243 77c40 14 49 39 46 71l-9 72c-18 15-33 24-48 26l9-61-11-59Z" />
            <path fill="#f8d58c" d="M166 76c-2-19 9-29 27-31-14-15-8-32 10-41 20 10 25 30 10 40 21 5 32 16 28 34l-36 19Z" />
            <path fill="none" stroke-width="3" d="M192 63c14-14 32-2 22 9-7 8-18-2-9-6" />
            <path fill="#fff3cd" d="m76 130 5-21 13 3 13-20 14 9 19-9 9 17 18 7-10 20-17 10m68-15 6-24 15 5 17-12 13 11 18-1 6 17 16 7-6 22" />
            <ellipse cx="126" cy="153" rx="42" ry="41" fill="#fff4d6" />
            <ellipse cx="256" cy="154" rx="39" ry="45" fill="#fff4d6" />
            <g class="lion-eyes">
              <ellipse cx="135" cy="153" rx="25" ry="29" fill="#91b79b" />
              <ellipse cx="247" cy="153" rx="24" ry="31" fill="#91b79b" />
              <ellipse cx="140" cy="157" rx="10" ry="16" fill="#4c4635" />
              <ellipse cx="242" cy="157" rx="10" ry="16" fill="#4c4635" />
              <circle cx="144" cy="151" r="4" fill="#fffdf0" stroke="none" /><circle cx="246" cy="151" r="4" fill="#fffdf0" stroke="none" />
            </g>
            <path fill="#f8d076" d="M164 111q27-28 52 0l-23 24Z" />
            <path fill="#8db09a" d="M165 169c-20 5-30 25-17 38 19 16 66 13 81-1 13-15 0-37-18-37-14-20-36-19-46 0Z" />
            <path fill="#648977" stroke-width="3" d="M171 184q7-10 14 1m17-1q7-10 14 0" />
            <path fill="#fff0c7" d="M150 207c-27-11-39-8-44 6 13 1 17 3 25 9l-11 8c21 9 41-1 57-10m42-10c29-12 49-1 49 12l-22 1 9 13c-26 5-37-7-48-16" />
            <path fill="#673f33" d="M145 236q48 28 99-1l-9 36q-42 24-85-1Z" />
            <path fill="#fff6d5" stroke-width="3" d="m151 239 10 20h65l9-19m-51 9v10m19-10v10" />
            <path fill="#ed9870" stroke-width="2.5" d="M168 276q25-23 53-1" />
            <g class="lion-beard" fill="#f8d58e" stroke-width="3.5">
              <path d="M141 268q18 14 48 17l-1 60-13-17-11 3-11-39-13 5-12-22Zm57 18q31 0 49-20l13 11-12 23-11-9-12 37-11-2-13 19Z" />
              <path fill="none" d="m158 282 12 29m53-18-1 20" />
            </g>
            <path fill="none" stroke="#f8cb6c" stroke-width="5" d="m101 197-16 9m212-13 14 10m-185-116 8-10m112 9-7-8" />
            <path fill="none" stroke-width="2.5" d="m95 142 8-8m13-11 10-2m-32 43 5 10m158-57 9 3m17 19 4 9m-2 24-5 7" />
          </g>
        </g>
      </svg>
    </div>

    <div class="heritage-bird">
      <svg viewBox="0 0 190 130" focusable="false">
        <g class="paper-bird" fill="#f7d999" stroke="#895340" stroke-width="3" stroke-linejoin="round">
          <path d="m14 76 52-11 21-54 30 39 55-20-39 48-2 25-34-11-18 28-1-35Z" />
          <path fill="#e77950" d="m66 65 42 14-21-68m21 68 64-49-39 48-2 25Z" />
          <path fill="none" d="m78 85 30-6-11 13" />
        </g>
      </svg>
    </div>
  </div>
</template>

<style scoped>
.heritage-window { position: absolute; width: clamp(270px, 30vw, 420px); aspect-ratio: 1; left: -7%; top: 5%; opacity: .72; }
.heritage-lanterns { position: absolute; width: clamp(230px, 28vw, 360px); aspect-ratio: 36 / 53; right: 2%; top: -6%; }
.heritage-town { position: absolute; inset: auto 0 -2px; height: clamp(200px, 34vh, 360px); }
.heritage-lion { position: absolute; width: clamp(240px, 29vw, 380px); aspect-ratio: 10 / 11; left: -1%; bottom: 1%; }
.heritage-bird { position: absolute; width: clamp(110px, 13vw, 175px); aspect-ratio: 19 / 13; right: 9%; bottom: 19%; }
.lantern-sway { transform-origin: 0 -115px; animation: lantern-sway 7.5s ease-in-out infinite; }
.lantern-sway-later { animation-delay: -3s; animation-duration: 9s; }
.lantern-tassel { transform-origin: 0 104px; animation: tassel-sway 4s ease-in-out infinite; }
.lion-dance { transform-origin: 195px 402px; animation: lion-step 6.4s ease-in-out infinite; }
.lion-head { transform-origin: 195px 270px; animation: lion-nod 6.4s ease-in-out infinite; }
.lion-beard { transform-origin: 193px 280px; animation: tassel-sway 3.2s ease-in-out infinite; }
.lion-eyes { transform-box: fill-box; transform-origin: center; animation: lion-blink 9.7s linear infinite; }
.paper-bird { animation: bird-glide 11s ease-in-out infinite; }
.heritage-sparks { animation: heritage-glimmer 7s ease-in-out infinite; }
/* SVG use 内的流苏也通过继承变量跟随暂停状态。 */
.lantern-sway, .lantern-tassel, .lion-dance, .lion-head, .lion-beard, .lion-eyes, .paper-bird, .heritage-sparks { animation-play-state: var(--china-animation-play-state, running); }
@keyframes lantern-sway { 0%, 100% { transform: rotate(-4deg); } 50% { transform: rotate(4deg); } }
@keyframes tassel-sway { 0%, 100% { transform: rotate(4deg); } 50% { transform: rotate(-6deg); } }
@keyframes lion-step { 0%, 100% { transform: rotate(-2deg) translateY(0); } 42% { transform: rotate(2deg) translateY(-8px); } 65% { transform: rotate(1deg) translateY(0); } }
@keyframes lion-nod { 0%, 100% { transform: rotate(2deg); } 42% { transform: rotate(-4deg); } 70% { transform: rotate(-1deg); } }
@keyframes lion-blink { 0%, 70%, 74%, 100% { transform: scaleY(1); } 71.5%, 72.5% { transform: scaleY(.1); } }
@keyframes bird-glide { 0%, 100% { transform: translate(0, 0) rotate(-4deg); } 50% { transform: translate(12px, -15px) rotate(4deg); } }
@keyframes heritage-glimmer { 0%, 100% { opacity: .5; } 50% { opacity: .95; } }
@media (max-width: 767px) {
  .heritage-window { width: 195px; left: -63px; top: -12px; }
  .heritage-lanterns { width: 185px; top: -68px; right: -19px; }
  .heritage-town { height: 175px; }
  .heritage-lion { width: clamp(155px, 22vh, 200px); left: -6px; bottom: -4px; }
  .heritage-bird { width: 93px; right: 5%; bottom: 9%; }
}
@media (max-height: 540px) and (min-width: 768px) {
  .heritage-lion { width: 235px; bottom: -16px; }
  .heritage-lanterns { width: 230px; top: -80px; }
}
</style>
