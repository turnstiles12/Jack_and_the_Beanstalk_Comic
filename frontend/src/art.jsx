// Hand-drawn style SVG illustrations: scenes from Jack and the Beanstalk and Little Red Riding Hood.
// Everything is inline SVG (no image files), so it stays sharp at any size and loads instantly.
// The <g> pieces take x / y / s (scale) so the same figures are reused in the scenes, covers and page decorations.

const INK = "#3b2a20";   // soft brown outline colour used on the figures

const leafPath = "M0 0 C 14 -18 42 -14 50 0 C 42 14 14 18 0 0 Z";

/* ---------------- small reusable pieces ---------------- */

export function CloudG({ x = 0, y = 0, s = 1, fill = "#fff" }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      <path d="M-60 14 C-84 14 -84 -18 -58 -18 C-56 -44 -14 -50 -2 -28 C14 -52 56 -40 54 -12 C82 -12 82 14 58 14 Z" fill={fill} />
      <path d="M-50 8 C-30 14 30 14 48 8" stroke="#dbeaf6" strokeWidth="4" fill="none" strokeLinecap="round" />
    </g>
  );
}

export function SunG({ x = 0, y = 0, s = 1 }) {
  const rays = Array.from({ length: 12 }, (_, i) => i * 30);
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      {rays.map((a) => (
        <rect key={a} x="-3" y="-46" width="6" height="14" rx="3" fill="#ffc83d" transform={`rotate(${a})`} />
      ))}
      <circle r="26" fill="#ffd84d" stroke="#f5b82e" strokeWidth="3" />
      <circle cx="-8" cy="-3" r="2.6" fill={INK} /><circle cx="8" cy="-3" r="2.6" fill={INK} />
      <path d="M-8 6 Q0 13 8 6" stroke={INK} strokeWidth="2.4" fill="none" strokeLinecap="round" />
      <circle cx="-15" cy="4" r="4" fill="#ff9d7a" opacity=".55" /><circle cx="15" cy="4" r="4" fill="#ff9d7a" opacity=".55" />
    </g>
  );
}

export function LeafG({ x = 0, y = 0, r = 0, s = 1, fill = "#52b95f" }) {
  return (
    <g transform={`translate(${x} ${y}) rotate(${r}) scale(${s})`}>
      <path d={leafPath} fill={fill} stroke="#2b7a3d" strokeWidth="2.5" strokeLinejoin="round" />
      <path d="M2 0 L44 0" stroke="#2b7a3d" strokeWidth="2" strokeLinecap="round" opacity=".6" />
    </g>
  );
}

export function TreeG({ x = 0, y = 0, s = 1, fill = "#4fae5a", dark = "#2f8a43" }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      <rect x="-10" y="-8" width="20" height="62" rx="6" fill="#8a5a3a" stroke={INK} strokeWidth="3" />
      <circle cx="0" cy="-40" r="46" fill={fill} stroke={dark} strokeWidth="4" />
      <circle cx="-34" cy="-18" r="30" fill={fill} stroke={dark} strokeWidth="4" />
      <circle cx="34" cy="-18" r="30" fill={fill} stroke={dark} strokeWidth="4" />
      <circle cx="-34" cy="-18" r="26" fill={fill} /><circle cx="34" cy="-18" r="26" fill={fill} /><circle cx="0" cy="-40" r="42" fill={fill} />
      <circle cx="-14" cy="-52" r="7" fill="#7ad084" opacity=".7" /><circle cx="22" cy="-26" r="6" fill="#7ad084" opacity=".7" />
    </g>
  );
}

export function PoppyG({ x = 0, y = 0, s = 1 }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      <path d="M0 0 C-2 10 2 18 0 26" stroke="#2f8a43" strokeWidth="3" fill="none" strokeLinecap="round" />
      <circle cx="0" cy="-2" r="8" fill="#e63a4a" /><circle cx="0" cy="-2" r="3" fill="#3b2a20" />
    </g>
  );
}

export function BeanG({ x = 0, y = 0, r = 0, s = 1 }) {
  return (
    <g transform={`translate(${x} ${y}) rotate(${r}) scale(${s})`}>
      <ellipse rx="9" ry="6" fill="#c9893a" stroke={INK} strokeWidth="2" />
      <path d="M-4 -1 Q0 -3 4 -1" stroke="#f0c37a" strokeWidth="1.8" fill="none" strokeLinecap="round" />
    </g>
  );
}

export function EggG({ x = 0, y = 0, s = 1 }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      <path d="M0 -14 C12 -14 14 6 0 10 C-14 6 -12 -14 0 -14 Z" fill="#ffd23f" stroke="#e0a21a" strokeWidth="2.5" />
      <path d="M-5 -6 Q-5 -10 -1 -10" stroke="#fff2a8" strokeWidth="2.5" fill="none" strokeLinecap="round" />
    </g>
  );
}

export function HenG({ x = 0, y = 0, s = 1 }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      <ellipse cx="0" cy="0" rx="26" ry="20" fill="#fff6e0" stroke={INK} strokeWidth="3" />
      <path d="M-22 -4 C-36 -16 -30 -26 -20 -20" fill="#fff6e0" stroke={INK} strokeWidth="3" strokeLinejoin="round" />
      <circle cx="18" cy="-14" r="11" fill="#fff6e0" stroke={INK} strokeWidth="3" />
      <path d="M28 -14 L38 -11 L28 -8 Z" fill="#ff9f2e" stroke={INK} strokeWidth="2" strokeLinejoin="round" />
      <path d="M14 -25 q3 -9 7 0 q4 -8 6 2" fill="#e63a4a" stroke={INK} strokeWidth="2" />
      <circle cx="19" cy="-16" r="2" fill={INK} />
      <path d="M-4 2 Q6 10 16 2" stroke="#e8d3a0" strokeWidth="3" fill="none" strokeLinecap="round" />
    </g>
  );
}

export function CowG({ x = 0, y = 0, s = 1 }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      <rect x="-30" y="12" width="9" height="24" rx="4" fill="#fff" stroke={INK} strokeWidth="3" />
      <rect x="14" y="12" width="9" height="24" rx="4" fill="#fff" stroke={INK} strokeWidth="3" />
      <rect x="-14" y="14" width="9" height="24" rx="4" fill="#f4efe8" stroke={INK} strokeWidth="3" />
      <rect x="28" y="14" width="9" height="24" rx="4" fill="#f4efe8" stroke={INK} strokeWidth="3" />
      <ellipse cx="0" cy="0" rx="42" ry="24" fill="#fff" stroke={INK} strokeWidth="3" />
      <path d="M-18 -18 C-6 -26 4 -14 -6 -6 C-16 -4 -24 -10 -18 -18 Z" fill="#b7794a" />
      <path d="M16 4 C26 -2 34 8 26 14 C18 16 10 10 16 4 Z" fill="#b7794a" />
      <path d="M-42 -6 C-54 -8 -52 6 -46 10" fill="none" stroke={INK} strokeWidth="3" strokeLinecap="round" />
      <circle cx="-48" cy="12" r="4" fill="#b7794a" stroke={INK} strokeWidth="2" />
      <rect x="34" y="-34" width="34" height="30" rx="13" fill="#fff" stroke={INK} strokeWidth="3" />
      <ellipse cx="62" cy="-10" rx="12" ry="9" fill="#ffc9c0" stroke={INK} strokeWidth="3" />
      <circle cx="58" cy="-10" r="1.8" fill={INK} /><circle cx="66" cy="-10" r="1.8" fill={INK} />
      <circle cx="46" cy="-24" r="2.6" fill={INK} /><circle cx="58" cy="-26" r="2.6" fill={INK} />
      <path d="M38 -34 l-6 -10 l10 4 Z" fill="#f3e3b0" stroke={INK} strokeWidth="2.4" strokeLinejoin="round" />
      <path d="M62 -34 l4 -10 l6 8 Z" fill="#f3e3b0" stroke={INK} strokeWidth="2.4" strokeLinejoin="round" />
    </g>
  );
}

export function CottageG({ x = 0, y = 0, s = 1, roof = "#d9503f", wall = "#fff1d2" }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      <rect x="-48" y="-40" width="96" height="62" rx="5" fill={wall} stroke={INK} strokeWidth="3.5" />
      <path d="M-62 -38 L0 -88 L62 -38 Z" fill={roof} stroke={INK} strokeWidth="3.5" strokeLinejoin="round" />
      <rect x="-9" y="-6" width="22" height="28" rx="11" fill="#8a5a3a" stroke={INK} strokeWidth="3" />
      <circle cx="7" cy="9" r="1.8" fill="#ffd23f" />
      <rect x="-40" y="-28" width="20" height="18" rx="3" fill="#bfe9ff" stroke={INK} strokeWidth="3" />
      <path d="M-30 -28 V-10 M-40 -19 H-20" stroke={INK} strokeWidth="2" />
      <rect x="26" y="-82" width="12" height="26" fill="#b5674a" stroke={INK} strokeWidth="3" />
      <path d="M32 -88 q-8 -10 2 -18 q-2 -6 6 -10" stroke="#fff" strokeWidth="5" fill="none" strokeLinecap="round" opacity=".85" />
    </g>
  );
}

export function CastleG({ x = 0, y = 0, s = 1 }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      <rect x="-70" y="-90" width="34" height="90" fill="#cfc2ea" stroke={INK} strokeWidth="3.5" />
      <rect x="36" y="-90" width="34" height="90" fill="#cfc2ea" stroke={INK} strokeWidth="3.5" />
      <rect x="-34" y="-62" width="68" height="62" fill="#ddd2f2" stroke={INK} strokeWidth="3.5" />
      <path d="M-78 -88 L-53 -128 L-28 -88 Z" fill="#7b5fc0" stroke={INK} strokeWidth="3.5" strokeLinejoin="round" />
      <path d="M28 -88 L53 -128 L78 -88 Z" fill="#7b5fc0" stroke={INK} strokeWidth="3.5" strokeLinejoin="round" />
      <path d="M-34 -62 h10 v-10 h10 v10 h10 v-10 h10 v10 h10 v-10 h4 v10" fill="none" stroke={INK} strokeWidth="3" />
      <path d="M-53 -128 V-150" stroke={INK} strokeWidth="3" /><path d="M-53 -150 L-34 -143 L-53 -136 Z" fill="#e63a4a" stroke={INK} strokeWidth="2.5" strokeLinejoin="round" />
      <path d="M53 -128 V-148" stroke={INK} strokeWidth="3" /><path d="M53 -148 L72 -141 L53 -134 Z" fill="#ffd23f" stroke={INK} strokeWidth="2.5" strokeLinejoin="round" />
      <path d="M-14 0 V-22 a14 14 0 0 1 28 0 V0 Z" fill="#7a4e2e" stroke={INK} strokeWidth="3" />
      <rect x="-62" y="-70" width="10" height="16" rx="5" fill="#ffe27a" stroke={INK} strokeWidth="2.5" />
      <rect x="52" y="-70" width="10" height="16" rx="5" fill="#ffe27a" stroke={INK} strokeWidth="2.5" />
    </g>
  );
}

export function HarpG({ x = 0, y = 0, s = 1 }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      <path d="M-22 40 C-30 -10 -10 -44 18 -42 C34 -40 30 -20 14 -14 L-8 40 Z" fill="#ffd23f" stroke="#c98a12" strokeWidth="3.5" strokeLinejoin="round" />
      {[-12, -4, 4, 12].map((dx, i) => (
        <path key={i} d={`M${dx - 2} ${34 - i * 3} L${dx + 8} ${-22 + i * 5}`} stroke="#fff6c4" strokeWidth="2" opacity=".95" />
      ))}
      <circle cx="-4" cy="-6" r="0" fill="none" />
    </g>
  );
}

export function BasketG({ x = 0, y = 0, s = 1 }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      <path d="M-18 -14 C-18 -34 18 -34 18 -14" fill="none" stroke="#a8693a" strokeWidth="4" strokeLinecap="round" />
      <path d="M-24 -12 H24 L19 14 H-19 Z" fill="#d49a58" stroke={INK} strokeWidth="3" strokeLinejoin="round" />
      <path d="M-20 -3 H20 M-18 5 H18" stroke="#a8693a" strokeWidth="2" />
      <path d="M-26 -14 Q-12 -24 0 -16 Q12 -24 26 -14 L22 -9 H-22 Z" fill="#fff" stroke={INK} strokeWidth="3" strokeLinejoin="round" />
      <path d="M-14 -17 v6 M-4 -17 v6 M6 -17 v6 M16 -17 v6" stroke="#ef6a6a" strokeWidth="2.5" />
    </g>
  );
}

/* ---------------- the characters ---------------- */

// A generic cartoon hero in Jack's outfit (no real face: round, friendly, faces anyone).
export function JackG({ x = 0, y = 0, s = 1, climbing = false }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      <rect x="-14" y="18" width="10" height="26" rx="5" fill="#e9c98a" stroke={INK} strokeWidth="3" />
      <rect x="4" y="18" width="10" height="26" rx="5" fill="#e9c98a" stroke={INK} strokeWidth="3" />
      <rect x="-17" y="38" width="15" height="9" rx="4" fill="#7a4e2e" stroke={INK} strokeWidth="2.5" />
      <rect x="2" y="38" width="15" height="9" rx="4" fill="#7a4e2e" stroke={INK} strokeWidth="2.5" />
      <path d="M-20 -2 H20 L24 26 H-24 Z" fill="#e2772e" stroke={INK} strokeWidth="3.5" strokeLinejoin="round" />
      <path d="M-14 -2 L0 22 L14 -2" fill="#7a4e2e" stroke={INK} strokeWidth="3" strokeLinejoin="round" />
      {climbing
        ? (<><path d="M-18 4 L-30 -24" stroke="#f2c39b" strokeWidth="9" strokeLinecap="round" /><path d="M18 4 L28 -20" stroke="#f2c39b" strokeWidth="9" strokeLinecap="round" /></>)
        : (<><path d="M-20 4 L-34 20" stroke="#f2c39b" strokeWidth="9" strokeLinecap="round" /><path d="M20 4 L34 20" stroke="#f2c39b" strokeWidth="9" strokeLinecap="round" /></>)}
      <circle cx="0" cy="-22" r="22" fill="#f6cba5" stroke={INK} strokeWidth="3.5" />
      <path d="M-22 -26 C-22 -52 22 -52 22 -26 C12 -34 -12 -34 -22 -26 Z" fill="#43a954" stroke={INK} strokeWidth="3.5" strokeLinejoin="round" />
      <path d="M14 -40 L34 -36 L16 -30 Z" fill="#43a954" stroke={INK} strokeWidth="3" strokeLinejoin="round" />
      <circle cx="-8" cy="-20" r="2.8" fill={INK} /><circle cx="8" cy="-20" r="2.8" fill={INK} />
      <path d="M-7 -10 Q0 -3 7 -10" stroke={INK} strokeWidth="2.8" fill="none" strokeLinecap="round" />
      <circle cx="-15" cy="-12" r="4" fill="#ff9d7a" opacity=".55" /><circle cx="15" cy="-12" r="4" fill="#ff9d7a" opacity=".55" />
    </g>
  );
}

// A generic cartoon hero in the red hooded cloak.
export function RedG({ x = 0, y = 0, s = 1, basket = true }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      <rect x="-14" y="26" width="10" height="22" rx="5" fill="#8a5a3a" stroke={INK} strokeWidth="3" />
      <rect x="4" y="26" width="10" height="22" rx="5" fill="#8a5a3a" stroke={INK} strokeWidth="3" />
      <rect x="-17" y="42" width="15" height="9" rx="4" fill="#5c3a22" stroke={INK} strokeWidth="2.5" />
      <rect x="2" y="42" width="15" height="9" rx="4" fill="#5c3a22" stroke={INK} strokeWidth="2.5" />
      <path d="M-22 -4 C-30 14 -34 30 -36 36 L36 36 C34 30 30 14 22 -4 Z" fill="#d7263d" stroke={INK} strokeWidth="3.5" strokeLinejoin="round" />
      <path d="M0 -4 V36" stroke="#a31a2d" strokeWidth="2.5" />
      <path d="M-10 6 h20" stroke="#ffd23f" strokeWidth="3" strokeLinecap="round" />
      <path d="M-22 4 L-34 20" stroke="#f6cba5" strokeWidth="8" strokeLinecap="round" />
      <path d="M22 4 L36 14" stroke="#f6cba5" strokeWidth="8" strokeLinecap="round" />
      {basket && <BasketG x={38} y={24} s={0.75} />}
      <circle cx="0" cy="-22" r="22" fill="#f6cba5" stroke={INK} strokeWidth="3.5" />
      <path d="M-28 4 C-34 -30 -22 -56 0 -56 C22 -56 34 -30 28 4 C22 -2 16 -10 15 -22 C12 -34 -12 -34 -15 -22 C-16 -10 -22 -2 -28 4 Z" fill="#d7263d" stroke={INK} strokeWidth="3.5" strokeLinejoin="round" />
      <circle cx="-8" cy="-20" r="2.8" fill={INK} /><circle cx="8" cy="-20" r="2.8" fill={INK} />
      <path d="M-7 -10 Q0 -3 7 -10" stroke={INK} strokeWidth="2.8" fill="none" strokeLinecap="round" />
      <circle cx="-14" cy="-12" r="4" fill="#ff9d7a" opacity=".55" /><circle cx="14" cy="-12" r="4" fill="#ff9d7a" opacity=".55" />
    </g>
  );
}

// The big friendly-looking wolf (cheeky, not scary).
export function WolfG({ x = 0, y = 0, s = 1, peek = false }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      {!peek && (
        <>
          <path d="M-30 6 C-34 40 -26 60 -10 60 L30 60 C44 56 46 36 36 6 Z" fill="#8a94a1" stroke={INK} strokeWidth="3.5" strokeLinejoin="round" />
          <path d="M-14 10 C-16 36 -10 52 6 52 C22 52 26 34 20 10 Z" fill="#d8dee6" />
          <path d="M38 40 C60 36 70 14 62 0 C56 12 50 18 40 22" fill="#8a94a1" stroke={INK} strokeWidth="3.5" strokeLinejoin="round" />
        </>
      )}
      <path d="M-34 -22 L-30 -58 L-10 -40 Z" fill="#8a94a1" stroke={INK} strokeWidth="3.5" strokeLinejoin="round" />
      <path d="M34 -22 L30 -58 L10 -40 Z" fill="#8a94a1" stroke={INK} strokeWidth="3.5" strokeLinejoin="round" />
      <path d="M-28 -28 L-27 -48 L-16 -38 Z" fill="#f2b6c0" /><path d="M28 -28 L27 -48 L16 -38 Z" fill="#f2b6c0" />
      <path d="M-38 -14 C-38 -48 38 -48 38 -14 C38 14 22 20 0 20 C-22 20 -38 14 -38 -14 Z" fill="#8a94a1" stroke={INK} strokeWidth="3.5" strokeLinejoin="round" />
      <path d="M-24 -2 C-24 14 -12 20 0 20 C12 20 24 14 24 -2 C16 -8 -16 -8 -24 -2 Z" fill="#e6ebf1" />
      <ellipse cx="0" cy="-4" rx="8" ry="6" fill={INK} />
      <ellipse cx="-2.5" cy="-6" rx="2.6" ry="1.6" fill="#fff" opacity=".7" />
      <path d="M-14 6 Q0 18 14 6" stroke={INK} strokeWidth="3" fill="#fff" strokeLinejoin="round" />
      <path d="M-4 8 l2 5 l2 -5 M4 8 l2 5 l2 -5" fill="#fff" stroke={INK} strokeWidth="1.6" strokeLinejoin="round" />
      <ellipse cx="-17" cy="-22" rx="7" ry="8" fill="#fff" stroke={INK} strokeWidth="2.5" /><circle cx="-15" cy="-21" r="3.8" fill="#e0a21a" /><circle cx="-14.5" cy="-21" r="1.8" fill={INK} />
      <ellipse cx="17" cy="-22" rx="7" ry="8" fill="#fff" stroke={INK} strokeWidth="2.5" /><circle cx="15" cy="-21" r="3.8" fill="#e0a21a" /><circle cx="14.5" cy="-21" r="1.8" fill={INK} />
      <path d="M-26 -33 L-10 -29 M26 -33 L10 -29" stroke={INK} strokeWidth="3" strokeLinecap="round" />
    </g>
  );
}

/* ---------------- full scenes ---------------- */

function Stalk({ d, w = 22 }) {
  return (
    <>
      <path d={d} fill="none" stroke="#2b7a3d" strokeWidth={w + 8} strokeLinecap="round" />
      <path d={d} fill="none" stroke="#52b95f" strokeWidth={w} strokeLinecap="round" />
      <path d={d} fill="none" stroke="#7bd685" strokeWidth={w * 0.28} strokeLinecap="round" strokeDasharray="2 22" opacity=".85" />
    </>
  );
}

const STALK = "M200 440 C130 390 270 350 215 300 C160 250 300 230 262 180 C235 140 340 140 372 112";

// Hero illustration: Jack climbs the beanstalk toward the castle in the clouds.
export function BeanstalkScene({ className = "" }) {
  return (
    <svg className={className} viewBox="0 0 560 440" role="img" aria-label="Illustration: a hero climbing a giant beanstalk to a castle in the clouds">
      <SunG x={70} y={70} s={1} />
      <CloudG x={170} y={64} s={0.9} /><CloudG x={470} y={40} s={0.7} /><CloudG x={92} y={190} s={0.65} />
      <ellipse cx="420" cy="132" rx="118" ry="30" fill="#fff" />
      <CloudG x={344} y={126} s={0.9} /><CloudG x={488} y={128} s={1} />
      <CastleG x={420} y={116} s={0.78} />
      <HenG x={326} y={104} s={0.5} /><EggG x={300} y={112} s={0.6} />
      <Stalk d={STALK} />
      <LeafG x={196} y={410} r={-150} s={1.1} /><LeafG x={226} y={340} r={-20} s={1} />
      <LeafG x={172} y={284} r={-160} s={1} /><LeafG x={276} y={224} r={-14} s={1.05} />
      <LeafG x={236} y={186} r={-165} s={0.9} /><LeafG x={318} y={150} r={-30} s={0.85} />
      <JackG x={250} y={318} s={0.62} climbing />
      <path d="M0 440 L0 392 C90 372 160 392 230 410 C300 392 380 376 460 394 C510 404 540 398 560 392 L560 440 Z" fill="#7bc96f" stroke="#3f9a4c" strokeWidth="4" />
      <path d="M0 440 L0 414 C80 402 170 420 260 424 C360 410 470 412 560 416 L560 440 Z" fill="#5fb55f" />
      <CottageG x={78} y={372} s={0.78} />
      <CowG x={440} y={398} s={0.62} />
      <BeanG x={330} y={426} r={20} s={0.9} /><BeanG x={352} y={430} r={-10} s={0.8} /><BeanG x={150} y={432} r={30} s={0.8} />
    </svg>
  );
}

// Hero illustration: Red walks the forest path, the wolf peeks from behind a tree.
export function HoodScene({ className = "" }) {
  return (
    <svg className={className} viewBox="0 0 560 440" role="img" aria-label="Illustration: a hero in a red hooded cloak on a forest path, with a wolf peeking from behind a tree">
      <SunG x={470} y={66} s={0.95} />
      <CloudG x={110} y={60} s={0.85} /><CloudG x={300} y={96} s={0.6} />
      <path d="M0 250 C90 200 170 232 260 214 C360 192 450 220 560 196 L560 440 L0 440 Z" fill="#9bd67f" stroke="#5db35a" strokeWidth="4" />
      <path d="M0 300 C120 270 220 300 330 286 C430 272 500 290 560 280 L560 440 L0 440 Z" fill="#7bc96f" />
      <CottageG x={480} y={232} s={0.5} roof="#e0603f" />
      <path d="M300 440 C250 392 340 366 300 334 C262 304 360 296 392 268 C404 256 420 252 438 250" fill="none" stroke="#e8c98f" strokeWidth="44" strokeLinecap="round" />
      <path d="M300 440 C250 392 340 366 300 334 C262 304 360 296 392 268 C404 256 420 252 438 250" fill="none" stroke="#d6b274" strokeWidth="3" strokeDasharray="6 16" strokeLinecap="round" />
      <TreeG x={70} y={282} s={1.15} /><TreeG x={168} y={250} s={0.72} fill="#5ab864" />
      <TreeG x={522} y={300} s={1} /><TreeG x={410} y={238} s={0.6} fill="#5ab864" />
      <WolfG x={436} y={394} s={0.62} peek />
      <TreeG x={478} y={334} s={1.3} />
      <RedG x={250} y={338} s={1.12} />
      <PoppyG x={60} y={402} s={1} /><PoppyG x={100} y={418} s={0.9} /><PoppyG x={176} y={410} s={1.1} />
      <PoppyG x={380} y={414} s={0.95} /><PoppyG x={520} y={408} s={1} /><PoppyG x={340} y={398} s={0.8} />
    </svg>
  );
}

/* ---------------- book covers (the story "products") ---------------- */

function CoverFrame({ title, sub, children, accent }) {
  return (
    <svg viewBox="0 0 240 320" className="cover-svg" role="img" aria-label={`${title} comic cover`}>
      <defs>
        <clipPath id={`clip-${accent.replace("#", "")}`}><rect x="12" y="12" width="216" height="296" rx="10" /></clipPath>
      </defs>
      <rect x="6" y="8" width="228" height="304" rx="14" fill="#000" fillOpacity=".15" />
      <rect x="4" y="4" width="228" height="304" rx="14" fill={accent} stroke={INK} strokeWidth="4" />
      <g clipPath={`url(#clip-${accent.replace("#", "")})`}>{children}</g>
      <rect x="4" y="4" width="22" height="304" rx="12" fill="#000" fillOpacity=".18" />
      <path d="M26 6 V306" stroke="#fff" strokeOpacity=".35" strokeWidth="2" />
      <rect x="34" y="20" width="180" height="62" rx="12" fill="#fffaf0" stroke={INK} strokeWidth="3.5" />
      <text x="124" y="46" textAnchor="middle" className="cover-title" fontSize="15" fontWeight="800" fill={INK} fontFamily="Fredoka, Nunito, system-ui, sans-serif">{title}</text>
      <text x="124" y="70" textAnchor="middle" className="cover-sub" fontSize="12" fontWeight="600" fill="#6b5545" fontFamily="Nunito, system-ui, sans-serif">{sub}</text>
    </svg>
  );
}

export function JackCover() {
  return (
    <CoverFrame title="Jack & the Beanstalk" sub="a comic starring you" accent="#3a9d58">
      <rect x="12" y="12" width="216" height="296" fill="#bfe8ff" />
      <rect x="12" y="180" width="216" height="130" fill="#bfe8ff" />
      <CloudG x={60} y={120} s={0.6} /><CloudG x={190} y={142} s={0.5} />
      <ellipse cx="150" cy="150" rx="72" ry="18" fill="#fff" />
      <CastleG x={160} y={146} s={0.5} />
      <Stalk d="M96 312 C60 278 138 262 108 230 C80 204 150 196 138 156" w={14} />
      <LeafG x={84} y={286} r={-150} s={0.7} /><LeafG x={120} y={248} r={-20} s={0.65} /><LeafG x={92} y={214} r={-160} s={0.6} />
      <path d="M12 308 V272 C70 258 130 282 228 266 V308 Z" fill="#7bc96f" stroke="#3f9a4c" strokeWidth="3" />
      <JackG x={118} y={232} s={0.45} climbing />
      <CowG x={176} y={286} s={0.38} />
    </CoverFrame>
  );
}

export function RedCover() {
  return (
    <CoverFrame title="Little Red Riding Hood" sub="a comic starring you" accent="#d7263d">
      <rect x="12" y="12" width="216" height="296" fill="#fbe7a8" />
      <SunG x={190} y={128} s={0.55} />
      <path d="M12 196 C70 170 130 196 228 176 V308 H12 Z" fill="#9bd67f" />
      <path d="M12 248 C80 228 150 252 228 236 V308 H12 Z" fill="#7bc96f" />
      <TreeG x={50} y={228} s={0.7} />
      <WolfG x={176} y={252} s={0.34} peek />
      <TreeG x={200} y={240} s={0.62} />
      <RedG x={108} y={256} s={0.72} />
      <PoppyG x={40} y={290} s={0.8} /><PoppyG x={176} y={292} s={0.8} /><PoppyG x={78} y={296} s={0.7} />
    </CoverFrame>
  );
}

export function StoryCover({ id }) {
  return id === "red" ? <RedCover /> : <JackCover />;
}

/* ---------------- page decoration ---------------- */

// Rolling hills + trees along the bottom of every page.
export function Footscape() {
  return (
    <svg className="footscape" viewBox="0 0 1200 160" preserveAspectRatio="xMidYMax slice" aria-hidden="true">
      <path d="M0 160 V80 C120 40 240 110 380 78 C520 46 640 112 780 76 C920 40 1040 100 1200 62 V160 Z" fill="#a8dd8b" />
      <path d="M0 160 V112 C140 84 260 140 420 112 C580 84 700 140 860 108 C1000 80 1100 126 1200 100 V160 Z" fill="#7bc96f" />
      <TreeG x={80} y={96} s={0.55} /><TreeG x={150} y={108} s={0.38} fill="#5ab864" />
      <TreeG x={1110} y={92} s={0.6} /><TreeG x={1040} y={108} s={0.4} fill="#5ab864" />
      <CottageG x={300} y={118} s={0.34} /><CowG x={900} y={130} s={0.32} />
      <PoppyG x={470} y={134} s={0.8} /><PoppyG x={520} y={142} s={0.7} /><PoppyG x={720} y={138} s={0.8} /><PoppyG x={760} y={146} s={0.65} />
      <BeanG x={620} y={146} r={20} s={0.7} /><BeanG x={640} y={150} r={-20} s={0.6} />
    </svg>
  );
}

export function Logo({ size = 34 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" aria-hidden="true">
      <rect x="6" y="6" width="36" height="36" rx="9" fill="#2f8f4f" stroke={INK} strokeWidth="3" />
      <path d="M13 34 C14 30 22 30 24 34 C26 30 34 30 35 34 V17 C34 13 27 13 24 17 C21 13 14 13 13 17 Z" fill="#fffaf0" stroke={INK} strokeWidth="2.4" strokeLinejoin="round" />
      <path d="M24 17 V34" stroke={INK} strokeWidth="2.2" />
      <path d="M28 12 C31 7 37 9 36 14 C33 15 30 15 28 12 Z" fill="#7bd685" stroke={INK} strokeWidth="2" strokeLinejoin="round" />
    </svg>
  );
}
