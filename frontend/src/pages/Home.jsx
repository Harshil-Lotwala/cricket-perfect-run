import { Link } from "react-router-dom";
import { ArrowUpRight, Target, Trophy, Users } from "lucide-react";
import { GAME_MODES } from "../data/modes";

function Home() {
  const modes = Object.values(GAME_MODES);

  return (
    <main className="min-h-screen app-shell text-white">
      <section className="max-w-[1400px] mx-auto px-6 py-16 md:py-24">
        <div className="grid lg:grid-cols-[1.2fr_.8fr] gap-12 items-end mb-16">
          <div>
            <p className="eyebrow mb-4">CRICKET, REWRITTEN</p>
            <h1 className="text-5xl md:text-7xl font-black tracking-[-.055em] leading-[.92] mb-7">
              Build an XI.<br /><span className="text-lime-300">Never lose.</span>
            </h1>
            <p className="text-slate-400 text-lg max-w-2xl leading-relaxed">
              Draft one player at a time from real historical squads. Set your era, choose your opposition, and chase cricket’s rarest season: a perfect one.
            </p>
          </div>
          <div className="glass-panel rounded-none p-6 grid grid-cols-3 gap-4">
            <HeroStat icon={<Users size={18} />} value="11" label="Players" />
            <HeroStat icon={<Target size={18} />} value="1" label="Mission" />
            <HeroStat icon={<Trophy size={18} />} value="0" label="Losses" />
          </div>
        </div>

        <div className="flex items-end justify-between mb-5">
          <div>
            <p className="eyebrow mb-2">CHOOSE YOUR FORMAT</p>
            <h2 className="text-2xl font-black">Start a new run</h2>
          </div>
          <p className="text-slate-500 text-sm hidden md:block">Real teams · real seasons · seeded simulations</p>
        </div>

        <div className="grid md:grid-cols-2 gap-6">
          {modes.map((mode) => (
            <Link key={mode.id} to={`/draft/${mode.id}`} className="group glass-panel rounded-none p-7 hover:border-lime-300/60 transition duration-300">
              <div className="flex items-center justify-between mb-6">
                <span className="bg-lime-300/10 text-lime-300 px-3 py-1 rounded-none text-xs font-bold uppercase tracking-wider">{mode.tag}</span>
                <ArrowUpRight className="text-slate-600 group-hover:text-lime-300 group-hover:translate-x-1 group-hover:-translate-y-1 transition" />
              </div>
              <h2 className="text-3xl font-black mb-3 group-hover:text-lime-300 transition">{mode.title}</h2>
              <p className="text-slate-400 mb-6">{mode.description}</p>
              <div className="pt-5 border-t border-white/8 flex justify-between text-sm">
                <span className="text-slate-500">Perfect target</span>
                <span className="font-black">{mode.perfectTarget}–0</span>
              </div>
            </Link>
          ))}
        </div>
      </section>
    </main>
  );
}

function HeroStat({ icon, value, label }) {
  return <div className="text-center"><span className="text-lime-300 flex justify-center mb-2">{icon}</span><p className="text-3xl font-black">{value}</p><p className="text-[11px] uppercase tracking-wider text-slate-500">{label}</p></div>;
}

export default Home;
