<script setup lang="ts">
import { useId } from 'vue'
import WonderPortal from './WonderPortal.vue'

const starId = 'space-star-' + useId()
const stars = [
  [67, 98, 1], [336, 181, .7], [1214, 80, .9], [1462, 268, 1.2],
  [90, 507, .7], [1280, 552, .65], [1065, 329, .7], [342, 646, .65],
  [1544, 689, .8], [452, 73, .65], [1124, 696, .6], [244, 361, .5],
]
</script>

<template>
  <div class="scene-artwork space-art">
    <div class="space-portal"><WonderPortal cosmic /></div>
    <div class="space-stars">
      <svg viewBox="0 0 1600 950" preserveAspectRatio="xMidYMid slice" focusable="false">
        <defs>
          <path :id="starId" d="m0-12 4 9 9 3-9 4-4 10-3-10-10-4 10-3Z" />
        </defs>
        <g fill="#e9cf83" stroke="#667780" stroke-width="2.5" class="stars-breathe">
          <use v-for="(star, index) in stars" :key="index" :href="'#' + starId" :transform="`translate(${star[0]} ${star[1]}) scale(${star[2]})`" />
        </g>
        <g fill="#748a95" opacity=".6">
          <circle cx="204" cy="184" r="3" /><circle cx="62" cy="295" r="2.5" /><circle cx="354" cy="415" r="3" />
          <circle cx="1508" cy="443" r="3" /><circle cx="1225" cy="320" r="2.5" /><circle cx="1370" cy="709" r="3" />
          <circle cx="1380" cy="37" r="2.5" /><circle cx="405" cy="764" r="2.5" /><circle cx="49" cy="714" r="3" />
        </g>
        <path fill="none" stroke="#99aab4" stroke-width="2" stroke-dasharray="3 14" stroke-linecap="round" d="M-73 801C183 497 210 299 599 212s703 104 1080-134" />
        <g transform="translate(1326 550) rotate(-20)" stroke="#636880" stroke-width="3.5" stroke-linecap="round">
          <ellipse rx="36" ry="33" fill="#cbb5d7" />
          <path fill="#ac9dbb" stroke="none" d="M5-30c41 15 22 60-8 61 45 13 53-58 8-61Z" />
          <path fill="none" stroke="#728c8e" stroke-width="8" d="M-24-24c-77 9-30 47 39 29C73-11 53-31 30-26" />
          <path fill="none" stroke="#d7e9b8" stroke-width="3" d="M-24-24c-77 9-30 47 39 29C73-11 53-31 30-26" />
          <path fill="none" stroke-width="2" d="m-14-15 10-3m5 33 11-3" />
        </g>
      </svg>
    </div>

    <div class="space-rocket">
      <svg viewBox="0 0 240 390" focusable="false">
        <g transform="rotate(27 123 193)">
          <g class="rocket-cruise" stroke="#405663" stroke-width="4" stroke-linecap="round" stroke-linejoin="round">
            <g class="rocket-plume">
              <path fill="#d9b96b" stroke="#897c50" d="M94 266c-23 35 8 40 1 69l18-9 9 40 15-39 15 5c-9-35 26-37-1-70Z" />
              <path fill="#eff0a5" stroke="none" d="M108 267c-6 23 7 35 14 63 5-23 26-40 16-64Z" />
            </g>
            <path fill="#dc5d4c" d="m96 190-33 42-4 35 38-19m52-58 29 41 2 37-34-21" />
            <path fill="#f8f2dd" d="M95 89c0-36 16-58 26-70 18 17 29 42 30 70v163q-27 24-55 0Z" />
            <path fill="#c6d1bd" stroke="none" d="M133 46c13 18 17 34 18 43v163l-15 9Z" />
            <path fill="#de5b49" d="M95 89q-1-35 26-70 29 31 30 70Z" />
            <path fill="#f2be72" stroke-width="3" d="m96 110 55 1v13l-55-1Z" />
            <path fill="#84b8b1" stroke-width="3" d="M107 144q15-10 28 0v21q-13 13-28 0Z" />
            <path fill="none" stroke="#e6f4d8" stroke-width="3" d="m113 150 9-3" />
            <path fill="#de5b49" stroke-width="3" d="M96 238h54v16q-28 20-54-2Z" />
            <path fill="#748f8f" d="m86 242-8-80q4-19 10-19 8 8 10 20v83m53-1 1-84q3-14 9-17 8 7 9 20l-6 80" />
            <text x="123" y="190" text-anchor="middle" fill="#b24538" stroke="none" font-size="16" font-weight="800" font-family="sans-serif">中</text>
            <text x="123" y="213" text-anchor="middle" fill="#b24538" stroke="none" font-size="16" font-weight="800" font-family="sans-serif">国</text>
            <path fill="none" stroke="#9aa895" stroke-width="2" d="M105 228h35M107 98h8" />
          </g>
        </g>
      </svg>
    </div>

    <div class="space-moon">
      <svg viewBox="0 0 1600 380" preserveAspectRatio="xMidYMax slice" focusable="false">
        <g stroke="#758797" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round">
          <path fill="#c9cdd4" stroke="#95a2ac" d="m-50 223 62-69 39 30 42-54 26 20 46-68 24 9 70 120 57-34 75 79 46-15 92 64 429 11 57-69 56 23 80-121 32-3 47-66 34 32 51-27 77 111 60-28 26 45 47-5 51 61 29-22 53 39v167H-50Z" />
          <path fill="#dce0ce" d="M-50 248c246-48 323 27 544 43 189 14 310 61 492 17 210-51 464-76 662-51v146H-50Z" />
          <path fill="#bac6b8" stroke="none" d="M-20 301c111-26 228 42 347 29 134-15 288 75 474 43 191-34 240 4 409-44 132-38 291 27 412-25v99H-20Z" />
          <g fill="#a9b9ae">
            <path d="M38 297c9-16 57-21 90-12 26 8 16 24-21 28-35 5-73-1-69-16Z" />
            <path d="M335 328c16-18 66-13 81 0 17 14-34 20-58 15-17-3-26-9-23-15Z" />
            <path d="M1244 307c12-20 80-27 117-9 29 15-11 33-54 32-37-2-70-9-63-23Z" />
            <path d="M1450 352c5-11 47-15 65-6 19 10-12 21-36 19-19 0-32-5-29-13Z" />
          </g>
          <g fill="none" stroke="#edf0d9" stroke-width="3">
            <path d="M50 300q27-19 65-8m232 38q20-10 38-2m874-20q46-15 78 0" />
          </g>
          <g fill="none" stroke="#8a9e95" stroke-width="3">
            <path d="m190 287 18 3m-3 13 10 2m944 30 15-4m-98 43 34-5m-594-17 19 4m-41 10 10 3m904-81 20 4" />
          </g>
          <g fill="#a6b7b0">
            <path d="m184 256 10-23 18-4 22 28-27 9Z" />
            <path d="m1438 267 17-32 23 2 20 31-29 10Z" />
          </g>
        </g>
      </svg>
    </div>

    <div class="space-station">
      <svg viewBox="0 0 350 180" focusable="false">
        <g class="station-drift" stroke="#4c6770" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round">
          <path fill="none" stroke-width="8" d="M38 88h257" />
          <g fill="#85afb1">
            <path d="m25 40 93 4-6 100-89-5Zm219 3 83-3-1 99-83 5Z" />
            <path fill="none" stroke="#537f8c" stroke-width="2" d="m55 43-1 95m33-94-3 96m-59-74 91 3m-92 22 90 1m-89 23 87 3m158-74v96m29-97-1 96m-55-71 82-2m-82 29 82-1m-82 26 82-2" />
          </g>
          <path fill="#f6e9cb" d="m126 72 18-7h65l16 10v35l-18 8-64-2-17-9Z" />
          <path fill="#d2604d" d="M161 64v-14h27l7 15v53l-5 15h-28v-15Z" />
          <path fill="#dfdec6" d="M148 71v40m57-38v37m-71-26h18m48 1h15" />
          <path fill="#90b3aa" d="M167 81h20v20h-20Z" />
          <path fill="none" stroke-width="2.5" d="M175 50V29m-11 1h25m-37 92-14 17m-6 1 8-2" />
        </g>
      </svg>
    </div>

    <div class="space-rabbit">
      <svg viewBox="0 0 410 490" focusable="false">
        <ellipse cx="219" cy="457" rx="107" ry="12" fill="#8fa496" opacity=".23" />
        <g class="rabbit-drift" stroke="#3d5361" stroke-width="4.5" stroke-linecap="round" stroke-linejoin="round">
          <path fill="#86a29f" d="M265 263q44-9 48 11l-3 85-41 12Z" />
          <path fill="#f4efd9" d="M160 273c-31 5-46-33-61-30-17 0-27 23-6 35l52 35m122-40c35-4 41-21 51-30l17 15c-6 22-27 40-51 49" />
          <path fill="#f4efd9" d="m148 347-1 52-12 26q-6 17 11 18l39-1 14-77m30 1 9 60q1 17 16 18l31-4q11-6 2-17l-16-23 1-49" />
          <path fill="#cd6752" d="m136 424-2 13q0 12 15 10l35-2 5-18Zm107 1 4 19 39-1q14-5 5-18Z" />
          <path fill="#fcf1d7" d="M165 249c-25 20-26 53-23 91l9 34q57 25 111-2l17-28c5-40-2-74-24-92Z" />
          <path fill="#d5ddc4" stroke="none" d="M254 260c9 18 11 52 1 89l-7 22-23 12 37-11 17-28c6-48-8-74-25-84Z" />
          <path fill="#83a9a5" d="M165 293h68l6 49-74 2Z" />
          <path fill="#e9e8cd" stroke-width="3" d="M173 300h49v26h-49Z" />
          <text x="197" y="319" text-anchor="middle" fill="#c55444" stroke="none" font-size="15" font-weight="800" font-family="sans-serif">中国</text>
          <circle cx="175" cy="335" r="3" fill="#c7654e" stroke-width="2" /><path fill="none" stroke-width="2" d="M187 335h16m10 0h12M161 363l14 3m58-3 14-4" />
          <path fill="#df674f" d="M145 261q61 23 119-2l7 15q-66 29-133 1Z" />
          <path fill="#f7efd5" d="M113 183c-6-56 37-103 100-100 68-2 110 45 106 103 5 57-46 91-104 94-62 1-105-37-102-97Z" />
          <path fill="#aad2c5" d="M128 180c-3-49 36-84 88-83 60 1 92 36 88 88 0 47-35 78-87 79-53 1-90-33-89-84Z" />
          <path fill="#83b4ac" stroke="none" d="M258 110c58 35 45 132-21 150 75-3 93-113 21-150Z" />
          <path fill="#fff3d9" d="M180 177c-5-22-27-58-11-66 19-8 26 24 29 55 3-41 12-60 24-61 17-3 13 34 1 64 30-2 54 19 50 44-2 31-32 42-62 41-32 1-57-18-53-45 1-13 7-24 22-32Z" />
          <path fill="none" stroke="#d99b7f" stroke-width="5" d="m176 123 11 38m34-43-7 41" />
          <g class="rabbit-eyes" fill="#fffef0" stroke-width="3">
            <ellipse cx="188" cy="202" rx="15" ry="19" /><ellipse cx="231" cy="199" rx="16" ry="21" />
            <ellipse cx="191" cy="206" rx="5" ry="9" fill="#364f5a" /><ellipse cx="228" cy="203" rx="5" ry="9" fill="#364f5a" />
          </g>
          <path fill="#d78b74" stroke-width="2.5" d="m203 216 12 1-6 8Z" />
          <path fill="none" stroke-width="2.5" d="M209 225v7m-11-2q11 11 22-2m-43-52 9-3m42-5 10 2" />
          <path fill="none" stroke="#f6ffe0" stroke-width="7" d="M146 159q9-32 29-35m-35 52 1-3" />
          <path fill="#d1dcca" stroke-width="3" d="m111 177-5 9v27l13 9m199-45 9 10v23l-13 12" />
          <g class="rabbit-wave">
            <path fill="#fff3d9" d="M313 253c-8-15-19-26-12-32 5-6 11 8 16 6l-2-24c0-7 7-7 10-1l4 12 1-18c2-8 9-5 10 1l1 17 4-11c4-6 10-1 9 4l-3 24c0 12-8 23-18 29Z" />
            <path fill="#da7759" d="m310 252 21 14 8-10-23-14Z" />
            <path fill="none" stroke-width="2.5" d="m321 232 10 4" />
          </g>
          <path fill="none" stroke="#61786c" stroke-width="5" d="M90 166v247" />
          <g class="rabbit-flag">
            <path fill="#d85343" d="M92 168c30-13 46 10 79-2l-1 52c-32 13-51-9-78 3Z" />
            <g fill="#ffdd7d" stroke="none">
              <path d="m108 175 3 7 8 1-6 5 1 8-7-4-6 4 1-8-6-5 8-1Z" />
              <path d="m125 174 1.5 3 3.5.5-2.5 2 .5 3-3-1.5-2.5 1.5.5-3-2.5-2 3.5-.5Zm8 8 1 3 3 .5-2 2 .5 3-2.5-1.5-2.5 1.5.5-3-2-2 3-.5Zm0 11 1 3 3 .5-2 2 .5 3-2.5-1.5-2.5 1.5.5-3-2-2 3-.5Zm-9 9 1 3 3 .5-2 2 .5 3-2.5-1.5-2.5 1.5.5-3-2-2 3-.5Z" />
            </g>
          </g>
          <path fill="#f6ecd0" d="M80 249q14-9 20 3v16q-16 6-20-7Z" />
        </g>
      </svg>
    </div>
  </div>
</template>

<style scoped>
.space-portal { position: absolute; width: clamp(300px, 33vw, 470px); aspect-ratio: 1; top: -8%; right: -6%; opacity: .9; }
.space-stars { position: absolute; inset: 0; }
.space-rocket { position: absolute; width: clamp(160px, 17vw, 250px); aspect-ratio: 8 / 13; top: 8%; left: 3%; }
.space-moon { position: absolute; inset: auto 0 -2px; height: clamp(180px, 33vh, 370px); }
.space-station { position: absolute; width: clamp(180px, 22vw, 310px); aspect-ratio: 35 / 18; bottom: 16%; left: 3%; }
.space-rabbit { position: absolute; width: clamp(250px, 30vw, 390px); aspect-ratio: 41 / 49; right: 0; bottom: 0; }
.rocket-cruise { animation: rocket-cruise 10s ease-in-out infinite; }
.rocket-plume { transform-origin: 122px 266px; animation: rocket-plume 2.4s ease-in-out infinite; }
.station-drift { transform-origin: 175px 90px; animation: station-drift 13s ease-in-out infinite; }
.rabbit-drift { animation: rabbit-drift 8s ease-in-out infinite; }
.rabbit-wave { transform-origin: 326px 259px; animation: rabbit-wave 8s ease-in-out infinite; }
.rabbit-flag { transform-origin: 92px 168px; animation: rabbit-flag 5s ease-in-out infinite; }
.rabbit-eyes { transform-box: fill-box; transform-origin: center; animation: rabbit-blink 8.7s linear infinite; }
.stars-breathe { animation: stars-breathe 8s ease-in-out infinite; }
.rocket-cruise, .rocket-plume, .station-drift, .rabbit-drift, .rabbit-wave, .rabbit-flag, .rabbit-eyes, .stars-breathe { animation-play-state: var(--china-animation-play-state, running); }
@keyframes rocket-cruise { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-14px); } }
@keyframes rocket-plume { 0%, 100% { transform: scaleY(.9); } 50% { transform: scaleY(1.08); } }
@keyframes station-drift { 0%, 100% { transform: rotate(-7deg) translateY(0); } 50% { transform: rotate(2deg) translateY(-12px); } }
@keyframes rabbit-drift { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-9px); } }
@keyframes rabbit-wave { 0%, 34%, 70%, 100% { transform: rotate(0); } 44%, 60% { transform: rotate(11deg); } 52% { transform: rotate(-5deg); } }
@keyframes rabbit-flag { 0%, 100% { transform: skewY(-2deg); } 50% { transform: skewY(3deg); } }
@keyframes rabbit-blink { 0%, 61%, 65%, 100% { transform: scaleY(1); } 62.5%, 63.5% { transform: scaleY(.08); } }
@keyframes stars-breathe { 0%, 100% { opacity: .5; } 50% { opacity: 1; } }
@media (max-width: 767px) {
  .space-portal { width: 225px; top: -74px; right: -47px; }
  .space-rocket { width: 112px; top: -10px; left: -3px; }
  .space-moon { height: 155px; }
  .space-station { width: 139px; left: -16px; bottom: 12%; }
  .space-rabbit { width: clamp(170px, 25vh, 210px); right: -8px; bottom: -5px; }
}
@media (max-height: 540px) and (min-width: 768px) {
  .space-rabbit { width: 235px; bottom: -12px; }
  .space-rocket { width: 165px; top: -20px; }
  .space-station { width: 180px; bottom: 10%; }
}
</style>
