# Fevapp funksiyalar xaritasi (mashhur mobil muharrirlar bilan solishtirish)

Bu hujjat mashhur mobil video muharrirlarda (CapCut va shu kabilar) uchraydigan
**funksiya g'oyalari** ro'yxati va ularning Fevapp'dagi holati. Faqat g'oyalar
olinadi: ikonkalar, nomlar, logotip, ranglar, shablon/effekt kutubxonalari,
musiqa va kod ko'chirilmaydi. Fevapp o'z dizayni bilan, bepul va **oflayn** qoladi.

Oxirgi yangilanish: 2026-11-25.

Belgilar: ✅ bor · 🟡 qisman · ⬜ yo'q · ✖ rejada yo'q (internet yoki to'lov talab qiladi)

Qiyinlik: **O** — oson (1 kun), **O'** — o'rtacha, **Q** — qiyin (alohida bosqich)

## 1. Asosiy tahrir

| Funksiya | Fevapp | Qiyinlik | Izoh |
|---|---|---|---|
| Kesish (trim), bo'lish, o'chirish, nusxa, tartib | ✅ | — | |
| Undo / redo | ✅ | — | |
| Playhead joyiga qo'shish, "+" tugma | ✅ | — | |
| Klipni almashtirish (replace) | ✅ | — | Joyi, uzunligi, ovozi, filtri, o'tishi saqlanadi |
| Kadrni muzlatish (freeze frame) | ✅ | — | 3 s lik kadr, filtr va qirqish saqlanadi |
| Kesish/aylantirish/ko'zgu (crop, rotate, flip) | ✅ | — | Nisbat chiplari, chetlar, 90° burish, ko'zgu |
| Tezlik (doimiy) | 🟡 | — | 0.25×–4×; boshqalarda 0.1×–100× |
| Tezlik egri chizig'i (speed curve) | ⬜ | O' | `SpeedProvider` vaqtga qarab |
| Silliq slow-motion (optical flow) | ⬜ | Q | Kadr interpolyatsiyasi — og'ir |
| Teskari (reverse) | ⬜ | Q | Videoni teskari qayta kodlash kerak |
| Stabilizatsiya | ⬜ | Q | Harakatni tahlil qilish |
| Klip kirish/chiqish animatsiyalari | ⬜ | O' | Keyframe mexanizmi asosida |

## 2. Kadr va format

| Funksiya | Fevapp | Qiyinlik | Izoh |
|---|---|---|---|
| Nisbat (9:16, 16:9, 1:1, 4:5) | ✅ | — | |
| Fon rangi | ✅ | — | |
| Xira fon | ✅ | — | Preview'da taxminiy |
| Fon rasmi/naqsh | ⬜ | O | O'zimizning originallar bilan |

## 3. Matn

| Funksiya | Fevapp | Qiyinlik | Izoh |
|---|---|---|---|
| Matn, rang, o'lcham, fon, qalin, shrift turi | ✅ | — | |
| Preview'da surish/kattalashtirish | ✅ | — | |
| Kirish/chiqish animatsiyalari | 🟡 | O | 3 tasi bor; ko'paytirish oson |
| Kontur (stroke): rang va qalinlik | ✅ | — | Soya avtomatik (fon yo'q bo'lsa) |
| Shriftlar | 🟡 | O | 7 ta: Oddiy, Manrope (OFL, ilova ichida), Serif, Mono, Kursiv, Erkin, Ixcham (telefonning o'zidan). Ko'proq OFL shrift qo'shish mumkin |
| Matn → ovoz (TTS) | ⬜ | O' | Android'ning oflayn TTS dvigateli bilan |
| Avtomatik subtitr | ⬜ | Q | 4-bosqich: whisper.cpp (oflayn) |
| Tayyor matn uslublari | ⬜ | O' | O'zimiz chizgan uslublar |

## 4. Audio

| Funksiya | Fevapp | Qiyinlik | Izoh |
|---|---|---|---|
| Qurilmadan musiqa, videodan ovoz, ovozni ajratish | ✅ | — | |
| Ovoz yozish (voice-over) | ✅ | — | |
| Ovoz balandligi, fade, bo'lish, to'lqin shakli | ✅ | — | |
| Ritm belgilari (beats) | ⬜ | O' | Oflayn onset aniqlash, timeline'da nuqtalar |
| Ovoz o'zgartirgich | ✅ | — | Ingichka, Yo'g'on, Robot, Aks-sado, Radio — video va audio kliplar uchun |
| Shovqinni kamaytirish | ⬜ | Q | 4-bosqich: RNNoise |
| Audio tezligi | ⬜ | O | Klip tezligi kabi |
| Ovoz effektlari kutubxonasi | ⬜ | O' | Faqat o'zimiz yozgan/erkin litsenziyali tovushlar |

## 5. Vizual effektlar

| Funksiya | Fevapp | Qiyinlik | Izoh |
|---|---|---|---|
| Filtrlar (tayyor ko'rinishlar) | 🟡 | O | 7 ta; ko'paytirish oson |
| Yorug'lik, kontrast, to'yinganlik, iliqlik | ✅ | — | |
| HSL, egri chiziqlar, o'tkirlik, vinyetka | ⬜ | O' | GL shader (eksport) + preview yaqinlashuvi |
| Effektlar (glitch, blur, zoom va h.k.) | ⬜ | O' | Har biri alohida shader |
| Stikerlar | ⬜ | O' | Original, ilova ichida |
| O'tishlar: qora/oq orqali | ✅ | — | |
| O'tishlar: crossfade, slide, zoom | ⬜ | Q | Ikki video ustma-ust (compositor) kerak |

## 6. Qatlamlar (overlay)

| Funksiya | Fevapp | Qiyinlik | Izoh |
|---|---|---|---|
| Rasm ichida rasm (PiP) | ✅ | — | Ovozsiz |
| Aralashtirish rejimlari (blend: screen, multiply…) | ⬜ | O' | Compositor sozlamalari / shader |
| Niqob (mask: doira, yumaloq burchak, yarim, teskari) | ✅ | — | Ustki qatlam uchun; yumshoq chet (feather) — keyin |
| Chroma key (yashil fon) | ✅ | — | Ustki qatlam uchun; preview Android 13+ da |
| Fonni avtomatik olib tashlash | ⬜ | Q | 4-bosqich: MediaPipe segmentatsiya |
| Harakatni kuzatish (motion tracking) | ⬜ | Q | |

## 7. Kalit kadrlar (keyframe)

| Funksiya | Fevapp | Qiyinlik | Izoh |
|---|---|---|---|
| Matn va overlay: joy, o'lcham, shaffoflik | ✅ | — | |
| Burilish (rotation) | ⬜ | O | Mexanizm tayyor |
| Asosiy klip: zoom/pan | ⬜ | O' | Preview'da TextureView matritsasi |
| Ovoz balandligi keyframe'lari | ⬜ | O' | |

## 8. Eksport

| Funksiya | Fevapp | Qiyinlik | Izoh |
|---|---|---|---|
| 720p/1080p, 30/60 fps, galereya, ulashish | ✅ | — | |
| Fonda eksport (bildirishnoma bilan) | ✅ | — | |
| 2K/4K, bitreyt tanlash | ⬜ | O | Qurilma kodeki imkoniyatiga qarab |
| GIF / faqat audio eksport | ⬜ | O' | |

## 9. Rejaga kirmaydiganlar

| Funksiya | Sabab |
|---|---|
| Onlayn shablonlar, bulut saqlash, akkaunt | ✖ Fevapp to'liq oflayn |
| Bulutdagi AI generatsiya (video/rasm yaratish) | ✖ Internet talab qiladi |
| Pullik "Pro" funksiyalar | ✖ Fevapp doim bepul |

## Tavsiya etilgan navbat

1. ✅ Kesish/aylantirish/ko'zgu
2. ✅ Kadrni muzlatish va klipni almashtirish
3. ✅ Matn konturi + shriftlar (yana animatsiyalar — keyin)
4. **Klip kirish/chiqish animatsiyalari** va **asosiy klip zoom keyframe'lari**
5. ✅ Ovoz o'zgartirgich (audio tezligi — keyin)
6. ✅ Chroma key va niqob
7. **Crossfade o'tishlar**
8. 4-bosqich (oflayn AI): subtitr, fonni olib tashlash, shovqinni kamaytirish

## Manbalar

- [App Store sahifasi](https://apps.apple.com/us/app/-/id1500855883)
- [CapCut AI tools](https://www.capcut.com/resource/capcut-ai)
- [CapCut audio tools](https://www.capcut.com/tools/audio)
- [CapCut voice filters](https://www.capcut.com/tools/voice-filters)
- [TikTok Ads: About CapCut](https://ads.tiktok.com/help/article/about-capcut)
- [SlashGear: CapCut tricks](https://www.slashgear.com/1603789/capcut-video-editing-tricks-guide/)
- [Hollyland: CapCut video editing guide](https://www.hollyland.com/blog/topics/capcut-video-editing)
- [Filmora: CapCut mask](https://filmora.wondershare.com/video-editing/capcut-mask.html)
