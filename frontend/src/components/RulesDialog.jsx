import { Crown, Lock, Shield, Trophy, X } from "lucide-react";

const formatRules = [
  ["IPL", "One season-version per franchise, maximum four overseas players, then standings-based IPL playoffs."],
  ["ODI World Cup", "One season-version per country, 50-over matches, top four qualify for 1st-v-4th and 2nd-v-3rd semi-finals."],
  ["T20 World Cup", "One season-version per country, 20-over matches, top four qualify for the semi-finals."],
  ["World Test Championship", "One country per opponent, two innings per side, draws are possible, and the top two play the final."],
];

function RulesDialog({ onClose }) {
  return (
    <div className="fixed inset-0 z-[100] grid place-items-center bg-black/80 p-2 sm:p-4" onMouseDown={onClose} role="presentation">
      <section role="dialog" aria-modal="true" aria-labelledby="rules-title"
        className="max-h-[94vh] w-full max-w-3xl overflow-y-auto border border-slate-700 bg-[#0b1115] p-4 shadow-2xl sm:p-5 md:max-h-[90vh] md:p-8"
        onMouseDown={(event) => event.stopPropagation()}>
        <div className="mb-6 flex items-start justify-between gap-4">
          <div>
            <p className="eyebrow mb-1">GAME RULES</p>
            <h2 id="rules-title" className="text-3xl font-black">How to play Perfect Run</h2>
            <p className="mt-2 text-slate-400">Draft a legal XI, choose its leaders, and survive a real tournament structure.</p>
          </div>
          <button onClick={onClose} aria-label="Close rules" className="border border-slate-700 p-2 text-slate-300 hover:border-lime-300 hover:text-lime-300"><X size={20} /></button>
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <Rule icon={<Trophy size={18} />} title="1. Choose a format">Each format has its own data-backed seasons, opponents, match rules, qualification path, and perfect target.</Rule>
          <Rule icon={<Lock size={18} />} title="2. Set difficulty first">Hard Mode hides ratings. It must be enabled before your first pick and stays locked until you start a New Game.</Rule>
          <Rule icon={<Shield size={18} />} title="3. Draft a legal XI">Draft 11 unique players. Your XI must contain a wicketkeeper; if ten picks contain none, the final slot is reserved for one.</Rule>
          <Rule icon={<Crown size={18} />} title="4. Pick your leaders">Select a captain and an eligible wicketkeeper. Both choices affect the simulation, so changing either can change the result.</Rule>
        </div>

        <div className="mt-6 border border-slate-800 bg-slate-950 p-5">
          <h3 className="mb-3 text-lg font-black">Opponent and tournament rules</h3>
          <div className="space-y-3 text-sm text-slate-300">
            <p><strong className="text-white">Play-through season:</strong> only seasons at or before your selected cutoff can be drafted or faced.</p>
            <p><strong className="text-white">Historical Squads:</strong> real team-season squads. <strong className="text-white">Legacy XI:</strong> a stronger best-of-era challenge.</p>
            <p><strong className="text-white">No duplicate identities:</strong> a tournament can include only one version of any franchise or country, never several seasons of the same team.</p>
            {formatRules.map(([name, rule]) => <p key={name}><strong className="text-lime-200">{name}:</strong> {rule}</p>)}
          </div>
        </div>

        <div className="mt-4 border border-lime-300/40 bg-lime-300/5 p-5">
          <h3 className="text-lg font-black text-lime-200">Hard Mode leaderboard</h3>
          <p className="mt-2 text-sm text-slate-300">Results are temporary and are never saved in browser storage. If a completed Hard Mode run is unbeaten (zero losses), Results unlocks an upload form. Nothing is published until you approve the consent box and press Post run. The server rebuilds the XI and replays the exact seed before publishing it. Normal Mode and losing runs cannot be uploaded.</p>
        </div>
        <div className="mt-4 border border-red-400/40 bg-red-400/5 p-5">
          <h3 className="text-lg font-black text-red-300">Post-simulation swaps</h3>
          <p className="mt-2 text-sm text-slate-300">Every format gives a new run two swaps after simulation. Choose one drafted player to remove, confirm the swap, draft exactly one replacement, and simulate again. A confirmed swap is consumed immediately; Hard Mode remains locked, and captain or keeper must be reselected if that player was removed.</p>
        </div>
      </section>
    </div>
  );
}

function Rule({ icon, title, children }) {
  return <div className="border border-slate-800 bg-slate-900 p-4"><h3 className="mb-2 flex items-center gap-2 font-black text-white"><span className="text-lime-300">{icon}</span>{title}</h3><p className="text-sm leading-6 text-slate-400">{children}</p></div>;
}

export default RulesDialog;
