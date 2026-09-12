# Call Audio: Honest Diagnosis & Tuning Guide

Read this before touching any audio setting. This document tells you what software
CAN and CANNOT do, how to find out which audio path your call actually uses, and how
to test every configuration one by one until you find the one that works on YOUR PC.

**What we promise:** correct dialing, correct call states, correct diagnostics.
**What we do NOT promise:** "crystal clear" audio. Nobody honest can promise that,
because call audio quality is decided by your mobile carrier and your Bluetooth/PC
hardware - not by the CRM, and not by this bridge.

---

## 1. The architecture fact that explains everything

```
CRM -> backend -> bridge (dial command ONLY) -> adb -> phone dialer -> SIM call
```

**Call audio NEVER passes through the CRM or the bridge.** The bridge only tells the
phone "dial this number". After that, your phone makes an ordinary GSM call on your
SIM. There are exactly two possible audio paths, and which one you get depends only
on where you physically talk:

| Path | Where you talk | Audio route | Who decides quality |
|---|---|---|---|
| A | On the phone itself | Phone mic/speaker <-> carrier GSM | Carrier + phone hardware |
| B | On the PC (Phone Link) | Phone <-> PC over **Bluetooth HFP** <-> Windows mic/speakers | Bluetooth HFP + Windows + carrier |

There is no third path. No CRM setting can move you from A to B or improve either one.

## 2. The honest limitations (read twice)

- **Path A is a normal phone call.** If it sounds bad, the cause is signal strength,
  carrier, or the phone's mic. Windows and the CRM have ZERO influence. Any "fix" on
  the PC would be a placebo.
- **Path B uses Bluetooth HFP (Hands-Free Profile).** This is the profile car
  kits use. Its known, inherent limits:
  - Narrowband audio on most devices (CVSD ~8 kHz; wideband mSBC ~16 kHz only if BOTH
    phone and PC radio negotiate it). It will never sound like a webinar recording.
  - HFP can degrade to half-duplex under weak signal (you may cut the other side off).
  - Phone Link REQUIRES the phone to be Bluetooth-paired with the PC for calls. If BT
    is flaky, calls get flaky - that is Phone Link/Windows territory, not ours.
- **The CRM cannot add "audio enhancement".** We deliberately ship no EQ, no noise
  suppression, no "HD voice" toggles, because applying blind enhancement to a path we
  cannot measure makes calls WORSE, not better. If a future option appears in this
  CRM it will be because it was tested, not guessed.
- **This backend stays provider-agnostic.** Phone Link is an audio convenience. The
  bridge works with or without it; the TelephonyService contract does not depend on
  any Microsoft feature.

## 3. Find out which path you are on (60 seconds)

1. Place a test call from the CRM.
2. When the callee answers: pick up the **phone handset** and talk. Can you hear them
   on the phone? -> Path A is available (it always is; it is the SIM call).
3. Now stay quiet and talk into the **PC microphone** with PC speakers on. Can you
   hear them on the PC speakers? -> Phone Link answered the call on the PC: Path B.
4. Both at once is normal too (phone + PC ring; you answer where you want).

## 4. Tuning procedure: test ONE config at a time, write the result

Rule: change ONE thing, make a 15-second test call, record pass/fail in the table in
section 6. Never change two things between tests - you will not know what helped.

### Step 1 - Baseline: is the SIM call itself good?

- Turn OFF PC Bluetooth (removes Path B from the equation).
- Call your own second number (or a colleague's) from the CRM, answer on the phone.
- Bad here = carrier/network/SIM problem. Stop tuning the PC; move to a window or
  contact your carrier. Nothing in this repo can help.

### Step 2 - Windows microphone selection (Path B)

- `Windows Settings > System > Sound > Input`
  - Choose the EXACT microphone (e.g. "Microphone (Realtek Audio)") - do not leave it
    on "Default" while testing.
  - Speak: the input level bar must move. If it does not move, nothing downstream
    (Phone Link included) can hear you.
  - Open `Input properties` (the device, not the page): set Volume 100; disable
    "Audio enhancements" for this test round.

### Step 3 - Windows output (can you hear THEM?)

- `Windows Settings > System > Sound > Output` - pick the exact speakers/headset.
- During a call, open the volume mixer and confirm Phone Link is not muted.

### Step 4 - Bluetooth reality check

- If a Bluetooth headset is paired, Windows may silently route the CALL to it while
  your media plays on speakers. During a test call check
  `Settings > System > Sound`: the active input/output devices with a green check are
  the ones actually carrying the call.
- For clean tests: disconnect other BT audio devices; test PC built-in mic+speakers
  first, then your headset, and keep whichever passes.

### Step 5 - Phone Link settings

- Phone Link app > `Calls` (left sidebar) > make sure calls are enabled and the
  Bluetooth pairing between phone and PC shows "Connected".
- On the phone: Bluetooth settings > PC name > enable **Call audio** (HFP) toggle if
  present.
- Windows privacy: `Settings > Privacy & security > Microphone` - "Let apps access
  your microphone" ON, and Phone Link allowed.

### Step 6 - Advanced (only if steps 1-5 did not find the problem)

- `Control Panel > Sound > [your mic] > Properties > Advanced`
  - Default format: `2 channel, 16 bit, 48000 Hz (DVD Quality)`.
  - Uncheck "Give exclusive mode applications priority" while testing.
- Phone mic on Path B: on the phone, Bluetooth > PC gear icon > enable "Call audio"
  (HFP), and disable "Media audio" temporarily if media playback keeps stealing the
  route.

## 5. Call audio line in the CRM (why it says "cannot be measured")

The Calls page **Call health** panel shows a gray "Call audio" line on purpose.
Software here can see: bridge reachable, phone on adb, SIM ready. It CANNOT see
sound. So the panel asks you to do the only real test: a short call where you check
BOTH directions (you hear them AND they hear you). That is not a limitation of this
CRM - it is the truth for every CRM on earth that dials through a phone.

## 6. The 15-scenario verification matrix

Do these once after setup and again whenever calling "suddenly stops working".
Tick the box only when you OBSERVED the expected result. Numbers 1-8 verify calling
logic end to end; 9-15 verify audio-path configurations from section 4.

| # | Scenario | Steps | Expected result | Pass |
|---|---|---|---|---|
| 1 | Bridge down | Stop the bridge (`Ctrl+C`), press Call on a lead | Honest error, CRM does not hang; Call Health shows bridge red | [ ] |
| 2 | Bridge up, phone disconnected | Bridge running, unplug USB, press Call | Honest "device offline" error; Android line red in Call Health | [ ] |
| 3 | Bridge up, phone connected | Reconnect USB, wait for ONLINE, press Call | Phone's dialer opens with the number; CRM call becomes RINGING | [ ] |
| 4 | SIM absent/airplane | Enable airplane mode, press Call | Bridge reports FAILED; honest error, no fake "ringing" | [ ] |
| 5 | Answered call | Call a friendly number, answer | CRM state CONNECTED; duration counts | [ ] |
| 6 | No answer | Let it ring out | CRM state NO_ANSWER automatically | [ ] |
| 7 | Busy / declined | Decline the call on the target phone | CRM state BUSY (or FAILED per device); honest state | [ ] |
| 8 | Outcome + follow-up | Set outcome "CALL_BACK_LATER" + follow-up on the call | Call row finalized; follow-up task created; timeline entry visible | [ ] |
| 9 | Baseline SIM quality (Step 1) | BT off, answer on phone | Clear both directions on the handset | [ ] |
| 10 | PC mic selected (Step 2) | Exact mic chosen, level bar moves while talking | Windows sees your voice | [ ] |
| 11 | PC output (Step 3) | Exact speakers chosen, mixer not muted | You hear the caller on the PC | [ ] |
| 12 | Built-in PC path (Step 4) | Other BT audio disconnected, test call | Both directions work on PC mic+speakers | [ ] |
| 13 | Bluetooth headset path (Step 4) | Headset connected, test call, check active devices | Both directions work on the headset | [ ] |
| 14 | Phone Link enabled (Step 5) | Calls enabled + BT "Connected" + call-audio toggle on | Call can be answered/talked from the PC | [ ] |
| 15 | Browser mic permission (Call health) | Click "Check microphone" on Calls page | Green "Working" line, or exact fix message shown | [ ] |

If 1-8 pass, the CRM integration is correct. If 9-14 have at least one fully passing
audio configuration, you are done - keep that configuration and stop. If none of
9-14 pass both directions, the problem is Windows/Bluetooth/driver level outside the
CRM; the fastest pragmatic fallback is Path A (talk on the phone, which always works
when signal is fine).

## 7. Where the CRM fits in (summary)

| Concern | Owner | CRM visibility |
|---|---|---|
| Dialing the number | Bridge + adb + phone | Call states (RINGING/CONNECTED/...) in Call Health + call rows |
| Phone presence | adb via bridge | "Android phone (adb)" line |
| SIM readiness | `getprop gsm.sim.state` via bridge | "SIM" line |
| Bridge liveness | Backend probe of bridge `/status` | "Phone bridge" line |
| Device heartbeat | Bridge heartbeat to backend | "Calling device" line |
| Microphone (browser) | getUserMedia probe | "Microphone" line |
| Call sound quality | Carrier + Windows + Bluetooth | **Not measurable - test call required** |
