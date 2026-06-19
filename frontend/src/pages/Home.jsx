import { Link } from "react-router-dom";
import { GAME_MODES } from "../data/modes";

function Home() {
  const modes = Object.values(GAME_MODES);

  return (
    <main className="min-h-screen bg-slate-950 text-white">
      <section className="max-w-7xl mx-auto px-6 py-16">
        <div className="mb-12">
          <p className="text-blue-400 font-semibold mb-3">CRICKET PERFECT RUN</p>
          <h1 className="text-5xl md:text-7xl font-black tracking-tight mb-6">
            Draft cricket's impossible teams.
          </h1>
          <p className="text-slate-400 text-lg max-w-2xl">
            Roll random historical squads, draft your XI one reveal at a time, then run a season
            path against historical squads or boss-mode Legacy XIs. Go for the perfect run.
          </p>
        </div>

        <div className="grid md:grid-cols-2 gap-6">
          {modes.map((mode) => (
            <Link
              key={mode.id}
              to={`/draft/${mode.id}`}
              className="group bg-slate-900 border border-slate-800 rounded-3xl p-8 hover:border-blue-500 transition shadow-xl"
            >
              <div className="flex items-center justify-between mb-5">
                <span className="inline-block bg-blue-500/10 text-blue-400 px-3 py-1 rounded-full text-sm">
                  {mode.tag}
                </span>
                <span className="text-xs text-slate-500">
                  Perfect: {mode.perfectTarget}-0 {mode.available ? "" : "• preview"}
                </span>
              </div>
              <h2 className="text-3xl font-bold mb-3 group-hover:text-blue-400 transition">
                {mode.title}
              </h2>
              <p className="text-slate-400">{mode.description}</p>
            </Link>
          ))}
        </div>
      </section>
    </main>
  );
}

export default Home;
