<script setup lang="ts">
import { useId } from 'vue'
import WonderPortal from './WonderPortal.vue'

const towerId = 'mountain-tower-' + useId()
</script>

<template>
  <div class="scene-artwork landscape-art">
    <div class="landscape-portal"><WonderPortal /></div>

    <div class="landscape-clouds">
      <svg viewBox="0 0 640 240" focusable="false">
        <g fill="#fff6dc" stroke="#708672" stroke-width="3.5" stroke-linejoin="round" stroke-linecap="round">
          <g class="cloud-drift">
            <path d="M34 100c-19-15-1-32 18-24-5-27 30-41 47-18 11-39 66-40 76-5 23-15 49 3 45 26 29-13 48 17 26 31H37c-26 0-24-22-3-22" />
            <path fill="none" d="M79 91c-5-22 28-30 33-12m14-23c18-18 42 3 25 17-9 8-22-2-14-8m37 35h49" />
            <path fill="none" d="M2 124h124m14 0h21m29-81h46" />
          </g>
          <g class="cloud-drift cloud-later" transform="translate(350 76) scale(.78)">
            <path d="M12 100c-14-21 10-42 32-27 4-35 43-40 58-12 30-25 63-3 57 19 31-5 39 26 10 34H26c-10 0-17-6-14-14Z" />
            <path fill="none" d="M64 83c-2-16 24-25 30-7m21 18h43M0 132h92" />
          </g>
        </g>
      </svg>
    </div>

    <div class="landscape-kite">
      <svg viewBox="0 0 160 250" focusable="false">
        <g class="kite-sail" stroke="#543c31" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round">
          <path fill="none" stroke="#a88e67" stroke-width="2" d="M83 105c-55 52 69 69 11 118" />
          <path fill="#df5545" d="m80 20 13 32 56-27-29 51 15 34-48-18-5 43-13-43-48 18 18-34L7 25l61 26Z" />
          <path fill="#f5c869" d="m80 20 1 65-42-9 29-25Zm1 65 39-9-27-24Z" />
          <path fill="none" d="m39 76 42 9 39-9M81 85l1 50" />
          <circle cx="69" cy="65" r="5" fill="#fff8df" /><circle cx="95" cy="65" r="5" fill="#fff8df" />
        </g>
      </svg>
    </div>

    <div class="landscape-horizon">
      <svg viewBox="0 0 1600 540" preserveAspectRatio="xMidYMax slice" focusable="false">
        <defs>
          <g :id="towerId" stroke="#4b5340" stroke-width="3" stroke-linejoin="round">
            <path fill="#d0c79b" d="m-26 0 3-51h8v9h12v-10H8v10h12v-9h9l3 51Z" />
            <path fill="#faf0c0" d="m-30-49 1-12h10v8h12v-8H5v8h12v-8h12l4 12Z" />
            <path fill="#66856c" d="M-5 0v-17q6-8 12 0V0Z" />
            <path fill="none" stroke-width="2" d="M-18-30h14m14 0h13M-24-12h11m26 0h13" />
          </g>
        </defs>
        <g stroke-linecap="round" stroke-linejoin="round">
          <path fill="#bed3ae" stroke="#809f82" stroke-width="3" d="M-20 246 17 201l18 16 43-77 37-4 22-59 32 49 21-4 47 84 31-29 24 14 52 122 76-64 60 111 103-28 113 87 96-27 132-70 88 52 101-107 43 34 87-117 22 8 50-68 24 20 37-45 39 50 29-5 69 103 27-18 45 52 65-54 96 106v225H-20Z" />
          <path fill="#81b397" stroke="#385f51" stroke-width="4" d="M-30 538V334l47-99 37 6L110 91l29-19 16 62 41 39 7 94 40-29 38 13 48 125 53-11 73 101 116 8 35 66Zm981 0 40-86 93-36 52-104 52 8 51-135 34 16 23-55 56-26 18 92 29 28 20-41 23 10 45 135 31-52 68 27 39 111 61-56 75 70v142Z" />
          <g fill="#aac878" stroke="#467660" stroke-width="3">
            <path d="m17 235 37 6L110 91l29-19-38 175-36 50-30 98-50 28Z" />
            <path d="m203 267 40-29 38 13-20 70 24 105-36-17-24-92Z" />
            <path d="m1136 312 52 8 51-135 34 16-8 76-48 76-11 72-42 35Z" />
            <path d="m1296 146 56-26-20 138-40 36-32 111 16-126Z" />
          </g>
          <g fill="none" stroke="#456d54" stroke-width="3">
            <path d="m127 142-14 64-24 39m65-58 14 30 4 59m-104 40-15 49m198-55 15 31-3 33m998-28-17 41m87-173-18 28-6 37m60-80 13 51 16 13m78 37 23 50m-1147-2 19 17 9 30" />
          </g>
          <path fill="#d4dcb0" stroke="#496c52" stroke-width="4" d="M-20 428c153-68 227 10 324 10 182 0 196 63 358 45 138-15 256-26 391-8 196 26 340-93 577-51v135H-20Z" />
          <path fill="#add4c3" stroke="#6a9786" stroke-width="3" d="M956 469c-174 2-162 20-303 37-42 5-30 21 88 32h391c-82-22-86-33-172-35-76-2-31-19 58-25Z" />
          <g fill="none" stroke="#eaf6d5" stroke-width="3">
            <path d="M785 496h92m-158 19h138m-44 15h197m-35-43h44" />
          </g>
          <path fill="none" stroke="#4b5340" stroke-width="25" d="M-15 347c90-98 151-122 245-19s197 140 343 112 217-41 338-7" />
          <path fill="none" stroke="#dbc58e" stroke-width="18" d="M-15 347c90-98 151-122 245-19s197 140 343 112 217-41 338-7" />
          <path fill="none" stroke="#fff0ba" stroke-width="5" stroke-dasharray="9 8" d="M-15 340c90-98 151-122 245-19s197 140 343 112 217-41 338-7" />
          <use :href="'#' + towerId" transform="translate(129 276) scale(1.08)" />
          <use :href="'#' + towerId" transform="translate(348 413) scale(.85)" />
          <use :href="'#' + towerId" transform="translate(746 420) scale(.6)" />
          <path fill="#5d8560" stroke="#365b46" stroke-width="4" d="M-10 493c87-30 119-4 186 9 72 13 113-10 167 23 39 25 68 8 105 26H-10Zm1213 53c49-49 102-20 130-46 43-40 120-9 162-34 47-29 100 9 127-8v94Z" />
          <g fill="none" stroke="#304f3d" stroke-width="3.5">
            <path d="m31 480 6-81m-2 22-19-14m20 35 19-17m-3 63 17-65m-8 35 20-8m-12-18-10-15m1390 83-10-71m8 30-24-9m25-7 12-18" />
          </g>
          <g fill="#93b56c" stroke="#3a6047" stroke-width="2.5">
            <path d="M37 420c-26 0-40-13-39-24 24-3 35 7 39 24Zm1 20c5-25 24-25 32-24-1 17-13 25-32 24Zm30-6c-10-25-5-37 5-44 12 14 12 29-5 44Zm1374 35c-27 1-40-9-40-20 25-6 38 2 40 20Z" />
          </g>
        </g>
      </svg>
    </div>

    <div class="landscape-traveler">
      <svg viewBox="0 0 330 390" focusable="false">
        <ellipse cx="164" cy="367" rx="80" ry="9" fill="#819b6c" opacity=".2" />
        <g class="panda-float" stroke="#293f36" stroke-width="4.5" stroke-linecap="round" stroke-linejoin="round">
          <path fill="#799778" d="m47 282 34 39 23-4 27 31 39-17 31 15 14-22 38-3 29-48-103-20Z" />
          <path fill="#b5ce8a" d="m47 282 27-23 42 6 43-20 43 13 39-4 41 19-35 16-63 4-35 11-41-13Z" />
          <path fill="none" stroke="#4d7155" stroke-width="3" d="m83 294 13 11 21-4m31 8-4 18m67-26-9 16m37-27 13-7" />
          <path fill="#d29a53" d="m197 185 33 4 9 62-31 7-23-57Z" />
          <path fill="#344b43" d="M107 211c-23 12-21 52 7 59l90-3c26-8 26-46 4-61Z" />
          <path fill="#fff6db" d="M125 192c-27 37-8 73 39 72 40-2 62-44 30-71Z" />
          <path fill="#31473e" d="M126 257c-3-13-31-15-38 0-6 16 6 25 28 22Zm63-4c9-17 35-10 38 3 6 18-11 25-27 22Z" />
          <path fill="#31473e" d="M115 212c-16-8-32-4-39 9-6 12 7 24 22 18l22-14m78-16c17-10 26-3 35 6 10 8 6 20-7 23l-27-11" />
          <path fill="none" stroke="#59844e" stroke-width="8" d="m71 196 7 91" />
          <path fill="#97bf66" stroke-width="3" d="M73 215c-21-7-25-22-20-32 20 5 24 18 20 32Zm-1-5c4-26 18-28 27-24-1 17-10 23-27 24Z" />
          <circle cx="109" cy="105" r="24" fill="#31473e" /><circle cx="207" cy="111" r="23" fill="#31473e" />
          <path fill="#fff7df" d="M91 153c-3-41 27-64 67-62 45-7 83 23 80 64 5 35-30 61-75 59-43 3-75-20-72-61Z" />
          <ellipse cx="129" cy="151" rx="22" ry="27" transform="rotate(23 129 151)" fill="#35493f" />
          <ellipse cx="198" cy="151" rx="21" ry="27" transform="rotate(-21 198 151)" fill="#35493f" />
          <g class="panda-eyes" fill="#fffcf0" stroke-width="2.5">
            <ellipse cx="136" cy="151" rx="9" ry="12" /><ellipse cx="191" cy="150" rx="10" ry="12" />
            <circle cx="139" cy="153" r="3.5" fill="#293f36" /><circle cx="194" cy="152" r="3.5" fill="#293f36" />
          </g>
          <path fill="#35493f" d="M154 172q10-8 20 0-10 15-20 0Z" />
          <path fill="none" stroke-width="3" d="M164 180v5m-11-1q11 13 24-1M130 119l9-1m47-1 10 2" />
          <path fill="#e3614e" stroke="none" d="M108 176q9-8 16 0-7 8-16 0m96 0q9-8 16 0-7 8-16 0" />
          <path fill="#d9503f" d="M120 207q42 15 79-3l8 13q-45 22-87 1Z" />
          <g class="panda-scarf">
            <path fill="#e86044" d="m191 218 31 5 33-9-11 16 15 12-37 2-28-15Z" />
            <path fill="none" stroke-width="2.5" d="m207 226 24 8" />
          </g>
          <path fill="#f1c465" stroke-width="2.5" d="m156 203 6 5 7-2-2 7 4 6-8 1-4 6-3-7-7-2 6-5Z" />
          <path fill="none" stroke-width="2.5" d="m121 108 9-3m41-8 9 2m-29-5-6 5" />
        </g>
      </svg>
    </div>
  </div>
</template>

<style scoped>
.landscape-portal { position: absolute; top: 5%; right: -2%; width: clamp(270px, 30vw, 440px); aspect-ratio: 1; opacity: .9; }
.landscape-clouds { position: absolute; top: 5%; left: -1%; width: min(53vw, 700px); aspect-ratio: 8 / 3; }
.landscape-kite { position: absolute; top: 19%; left: 6%; width: clamp(90px, 10vw, 145px); aspect-ratio: 16 / 25; }
.landscape-horizon { position: absolute; inset: auto 0 -2px; height: clamp(240px, 49vh, 550px); }
.landscape-traveler { position: absolute; bottom: 3%; right: 1%; width: clamp(200px, 24vw, 330px); aspect-ratio: 33 / 39; }
.panda-float { animation: panda-float 7s ease-in-out infinite; }
.panda-scarf { transform-box: fill-box; transform-origin: left center; animation: panda-scarf 3.8s ease-in-out infinite; }
.panda-eyes { transform-box: fill-box; transform-origin: center; animation: panda-blink 8.2s linear infinite; }
.cloud-drift { animation: cloud-drift 22s ease-in-out infinite alternate; }
.cloud-later { animation-delay: -9s; }
.kite-sail { transform-origin: 80px 55px; animation: kite-sail 9s ease-in-out infinite; }
.panda-float, .panda-scarf, .panda-eyes, .cloud-drift, .kite-sail { animation-play-state: var(--china-animation-play-state, running); }
@keyframes panda-float { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-12px); } }
@keyframes panda-scarf { 0%, 100% { transform: rotate(-3deg); } 50% { transform: rotate(7deg); } }
@keyframes panda-blink { 0%, 43%, 47%, 100% { transform: scaleY(1); } 44.5%, 45.5% { transform: scaleY(.08); } }
@keyframes cloud-drift { to { translate: 25px -5px; } }
@keyframes kite-sail { 0%, 100% { transform: rotate(-7deg) translateY(0); } 50% { transform: rotate(5deg) translateY(-8px); } }
@media (max-width: 767px) {
  .landscape-portal { width: 200px; top: -54px; right: -28px; }
  .landscape-clouds { width: 400px; top: 2%; left: -55px; }
  .landscape-kite { top: 3%; left: 5%; width: 82px; }
  .landscape-horizon { height: 225px; }
  .landscape-traveler { width: 180px; right: -26px; bottom: -12px; }
}
@media (max-height: 540px) and (min-width: 768px) {
  .landscape-traveler { width: 190px; bottom: -25px; }
  .landscape-portal { width: 230px; top: -50px; }
}
</style>
