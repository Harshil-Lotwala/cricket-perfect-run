import { Plane, Crown, Shield } from "lucide-react";

// Balanced cricket formation: 3 top order, 2 all-rounders, 2 finishers,
// 1 wicketkeeper, 2 pacers, 1 spinner (11 slots).
const SLOTS = [
  { id: "top1", cat: "TOP", label: "Top order", x: 22, y: 16 },
  { id: "top2", cat: "TOP", label: "Top order", x: 50, y: 12 },
  { id: "top3", cat: "TOP", label: "Top order", x: 78, y: 16 },
  { id: "ar1", cat: "AR", label: "All-rounder", x: 16, y: 40 },
  { id: "ar2", cat: "AR", label: "All-rounder", x: 84, y: 40 },
  { id: "fin1", cat: "FINISH", label: "Finisher", x: 30, y: 58 },
  { id: "fin2", cat: "FINISH", label: "Finisher", x: 70, y: 58 },
  { id: "wk", cat: "WK", label: "Keeper", x: 50, y: 74 },
  { id: "spin", cat: "SPIN", label: "Spinner", x: 84, y: 70 },
  { id: "pace1", cat: "PACE", label: "Pacer", x: 30, y: 86 },
  { id: "pace2", cat: "PACE", label: "Pacer", x: 64, y: 86 },
];

const CAT_COLOR = {
  TOP: "bg-blue-500",
  AR: "bg-emerald-500",
  FINISH: "bg-amber-500",
  WK: "bg-yellow-400",
  PACE: "bg-red-500",
  SPIN: "bg-violet-500",
};

// Assign drafted players to formation slots by role with graceful fallback.
function assign(team, keeperId) {
  const pool = [...team].sort((a, b) => b.rating - a.rating);
  const used = new Set();
  const take = (pred) => {
    const p = pool.find((x) => !used.has(x.id) && pred(x));
    if (p) used.add(p.id);
    return p || null;
  };

  const result = {};
  // Keeper first (prefer chosen keeper).
  result.wk =
    (keeperId != null && team.find((p) => p.id === keeperId && !used.has(p.id) && (used.add(p.id), true))) ||
    take((p) => p.keeperEligible) ||
    null;
  // Bowlers → spinner + pacers.
  result.spin = take((p) => p.role === "BOWL") || null;
  result.pace1 = take((p) => p.role === "BOWL") || null;
  result.pace2 = take((p) => p.role === "BOWL") || null;
  // All-rounders.
  result.ar1 = take((p) => p.role === "AR") || null;
  result.ar2 = take((p) => p.role === "AR") || null;
  // Top order + finishers → best remaining batters, then anyone left.
  result.top1 = take((p) => p.role === "BAT" || p.role === "WK") || null;
  result.top2 = take((p) => p.role === "BAT" || p.role === "WK") || null;
  result.top3 = take((p) => p.role === "BAT" || p.role === "WK") || null;
  result.fin1 = take(() => true) || null;
  result.fin2 = take(() => true) || null;
  // Fill any still-empty slot with whoever is left.
  for (const s of SLOTS) {
    if (!result[s.id]) result[s.id] = take(() => true) || null;
  }
  return result;
}

function FieldFormation({ team, captainId, keeperId }) {
  const assigned = assign(team, keeperId);

  return (
    <div
      className="relative w-full rounded-3xl overflow-hidden border border-emerald-900/60"
      style={{
        aspectRatio: "4 / 5",
        background:
          "radial-gradient(ellipse at center, #166534 0%, #14532d 55%, #0f3d22 100%)",
      }}
    >
      {/* pitch */}
      <div
        className="absolute left-1/2 -translate-x-1/2 bg-amber-200/20 border border-amber-100/20 rounded"
        style={{ top: "30%", height: "44%", width: "9%" }}
      />
      {/* 30-yard circle */}
      <div
        className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 rounded-full border border-white/10"
        style={{ width: "78%", height: "70%" }}
      />

      {SLOTS.map((slot) => {
        const player = assigned[slot.id];
        return (
          <div
            key={slot.id}
            className="absolute -translate-x-1/2 -translate-y-1/2 flex flex-col items-center w-24 text-center"
            style={{ left: `${slot.x}%`, top: `${slot.y}%` }}
          >
            <div
              className={`w-10 h-10 rounded-full flex items-center justify-center text-xs font-black shadow-lg ${
                player ? CAT_COLOR[slot.cat] + " text-black" : "bg-slate-800/70 border border-dashed border-slate-500 text-slate-400"
              }`}
            >
              {player ? player.name.split(" ").slice(-1)[0].slice(0, 3).toUpperCase() : ""}
              {player && captainId === player.id && (
                <Crown size={12} className="absolute -mt-8 text-yellow-300" />
              )}
            </div>
            <p className="mt-1 text-[11px] leading-tight font-semibold text-white/90 flex items-center gap-0.5">
              {player ? player.name : <span className="text-slate-400">{slot.label}</span>}
              {player?.overseas && <Plane size={10} className="text-sky-300" />}
              {player && keeperId === player.id && <Shield size={10} className="text-blue-200" />}
            </p>
          </div>
        );
      })}
    </div>
  );
}

export default FieldFormation;
