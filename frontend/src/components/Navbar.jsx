import { useState } from "react";
import { Link, NavLink } from "react-router-dom";
import { CircleDot } from "lucide-react";
import RulesDialog from "./RulesDialog";

function Navbar() {
  const [showRules, setShowRules] = useState(false);
  return (
    <>
    <nav className="bg-[#070b0d]/85 backdrop-blur-xl border-b border-white/10 sticky top-0 z-40">
      <div className="max-w-[1500px] mx-auto px-3 sm:px-6 h-16 flex items-center gap-2 sm:gap-8">
        <Link to="/" className="font-black text-lg flex items-center gap-2 tracking-tight">
          <span className="w-8 h-8 rounded-full bg-lime-300 text-slate-950 grid place-items-center"><CircleDot size={18} /></span>
          <span className="hidden min-[520px]:inline">PERFECT RUN</span>
        </Link>
        <div className="ml-auto flex min-w-0 gap-0 text-xs text-slate-400 sm:gap-1 sm:text-sm">
          {[["/", "Modes"], ["/draft/ipl", "Draft"], ["/result", "Results"], ["/leaderboard/ipl", "Leaderboard"]].map(([to, label]) => (
            <NavLink key={to} to={to} className={({ isActive }) => `px-2 py-2 rounded-none transition sm:px-3 ${isActive ? "bg-white/8 text-white" : "hover:text-white"}`}>
              {label}
            </NavLink>
          ))}
          <button onClick={() => setShowRules(true)} className="px-2 py-2 transition hover:text-white sm:px-3">Rules</button>
        </div>
      </div>
    </nav>
    {showRules && <RulesDialog onClose={() => setShowRules(false)} />}
    </>
  );
}

export default Navbar;
