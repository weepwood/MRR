<template>
  <span class="oss-folder-icon" aria-hidden="true">
    <span class="work-5" />
    <span class="work-4" />
    <span class="work-3" />
    <span class="work-2" />
    <span class="work-1" />
  </span>
</template>

<style scoped>
.oss-folder-icon {
  position: relative;
  display: inline-block;
  flex: 0 0 auto;
  width: 1em;
  height: 0.6667em;
  vertical-align: middle;
  perspective: 6.25em;
}

.work-5,
.work-4,
.work-3,
.work-2,
.work-1 {
  border-radius: 0.0667em;
}

.work-4,
.work-3,
.work-2,
.work-1 {
  position: absolute;
}

.work-5::before,
.work-5::after,
.work-1::before,
.work-1::after {
  position: absolute;
  content: "";
}

.work-5 {
  position: relative;
  z-index: 1;
  display: block;
  width: 100%;
  height: 100%;
  background: #d97706;
  border-top-left-radius: 0;
  transform-origin: top center;
  transition: box-shadow 300ms ease;
}

.work-5::after {
  bottom: 99%;
  left: 0;
  width: 33.333%;
  height: 0.0667em;
  background: #d97706;
  border-radius: 0.0667em 0.0667em 0 0;
}

.work-5::before {
  top: -0.0625em;
  left: 31.46%;
  width: 6.667%;
  height: 0.0667em;
  background: #d97706;
  clip-path: polygon(0 35%, 0 100%, 50% 100%);
}

.work-4,
.work-3,
.work-2 {
  inset: 0.0042em;
  transform-origin: bottom center;
  transition: transform 300ms ease;
}

.work-4 {
  z-index: 2;
  background: #a1a1aa;
}

.work-3 {
  z-index: 3;
  background: #d4d4d8;
}

.work-2 {
  z-index: 4;
  background: #e4e4e7;
}

.work-1 {
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 5;
  width: 100%;
  height: 0.65em;
  background: linear-gradient(to top, #f59e0b, #fbbf24);
  border-top-right-radius: 0;
  transform-origin: bottom center;
  transition: transform 300ms ease, box-shadow 300ms ease;
}

.work-1::after {
  right: 0;
  bottom: 99%;
  width: 60.833%;
  height: 0.0667em;
  background: #fbbf24;
  border-radius: 0.0667em 0.0667em 0 0;
}

.work-1::before {
  top: -0.0417em;
  right: 59.167%;
  width: 5%;
  height: 0.05em;
  background: #fbbf24;
  clip-path: polygon(100% 14%, 50% 100%, 100% 100%);
}

.oss-folder-icon:hover .work-4 {
  transform: rotateX(-20deg);
}

.oss-folder-icon:hover .work-3 {
  transform: rotateX(-30deg);
}

.oss-folder-icon:hover .work-2 {
  transform: rotateX(-38deg);
}

.oss-folder-icon:hover .work-1 {
  box-shadow: inset 0 0.0833em 0.1667em #fbbf24, inset 0 -0.0833em 0.1667em #d97706;
  transform: rotateX(-46deg) translateY(0.0042em);
}

.oss-folder-icon:hover .work-5 {
  box-shadow: 0 0.0833em 0.1667em rgb(0 0 0 / 20%);
}

@media (prefers-reduced-motion: reduce) {
  .work-4,
  .work-3,
  .work-2,
  .work-1,
  .work-5 {
    transition-duration: 200ms;
  }

  .oss-folder-icon:hover .work-4 {
    transform: rotateX(-18deg);
  }

  .oss-folder-icon:hover .work-3 {
    transform: rotateX(-27deg);
  }

  .oss-folder-icon:hover .work-2 {
    transform: rotateX(-36deg);
  }

  .oss-folder-icon:hover .work-1 {
    transform: rotateX(-44deg) translateY(0.0042em);
  }
}
</style>
