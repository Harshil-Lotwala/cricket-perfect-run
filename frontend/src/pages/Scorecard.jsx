import { Link, useParams } from "react-router-dom";
import useGameStore from "../store/useGameStore";

function Scorecard() {
  const { matchId } = useParams();
  const { getMatchById } = useGameStore();
  const match = getMatchById(decodeURIComponent(matchId));

  if (!match) {
    return (
      <main className="min-h-screen bg-slate-950 text-white flex items-center justify-center">
        <div className="bg-slate-900 border border-slate-800 rounded-none p-8 text-center">
          <h1 className="text-3xl font-black mb-3">Scorecard not found</h1>
          <Link to="/result" className="text-blue-400">
            Back to result
          </Link>
        </div>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-slate-950 text-white">
      <div className="max-w-5xl mx-auto px-6 py-12">
        <Link to="/result" className="text-blue-400 text-sm">
          ← Back to result
        </Link>
        <p className="text-blue-400 font-semibold mt-4 mb-1">{match.stage}</p>
        <h1 className="text-4xl font-black mb-1">Your XI vs {match.opponentLabel}</h1>
        <p className={`text-lg font-bold mb-1 ${match.drawn ? "text-yellow-300" : match.won ? "text-green-400" : "text-red-400"}`}>
          {match.margin}
        </p>
        <div className="mb-8 space-y-1 text-slate-400">
          {match.tossWinner && <p>Toss: <span className="text-slate-200">{match.tossWinner} won and chose to {match.tossDecision}</span></p>}
          <p>Player of the Match: <span className="text-slate-200">{match.playerOfMatch}</span></p>
        </div>

        {(match.innings?.length ? match.innings : [match.teamInnings, match.opponentInnings]).map((card, index) => (
          <Innings key={`${card.team}-${index}`} card={card} number={match.innings?.length > 2 ? Math.floor(index / 2) + 1 : null} />
        ))}
      </div>
    </main>
  );
}

function Innings({ card, number }) {
  return (
    <div className="bg-slate-900 border border-slate-800 rounded-none p-6 mb-8">
      <div className="flex items-baseline justify-between mb-4">
        <h2 className="text-2xl font-bold">{card.team}{number ? ` · ${number}${number === 1 ? "st" : "nd"} innings` : ""}</h2>
        <p className="text-3xl font-black">
          {card.runs}/{card.wickets}{" "}
          <span className="text-base text-slate-400 font-normal">({card.overs} ov)</span>
        </p>
      </div>

      <h3 className="text-slate-400 text-sm font-semibold mb-2">BATTING</h3>
      <div className="overflow-hidden rounded-none border border-slate-800 mb-5">
        <div className="grid grid-cols-[2fr_1fr_1fr_1fr] bg-slate-800 text-slate-300 text-xs font-semibold px-4 py-2">
          <span>Batter</span>
          <span className="text-center">R</span>
          <span className="text-center">B</span>
          <span className="text-center">SR</span>
        </div>
        {card.batting.map((line, i) => (
          <div
            key={line.name + i}
            className={`grid grid-cols-[2fr_1fr_1fr_1fr] px-4 py-2 text-sm ${
              !line.batted ? "bg-slate-900/30 text-slate-500" : i % 2 ? "bg-slate-900" : "bg-slate-900/50"
            }`}
          >
            <span>
              {line.name}{" "}
              <span className="text-slate-500 text-xs">
                {!line.batted ? "did not bat" : line.out ? "" : "not out*"}
              </span>
            </span>
            <span className="text-center font-semibold">{line.batted ? line.runs : "-"}</span>
            <span className="text-center">{line.batted ? line.balls : "-"}</span>
            <span className="text-center">
              {line.batted && line.balls > 0 ? ((line.runs / line.balls) * 100).toFixed(1) : "-"}
            </span>
          </div>
        ))}
      </div>

      <h3 className="text-slate-400 text-sm font-semibold mb-2">BOWLING</h3>
      <div className="overflow-hidden rounded-none border border-slate-800">
        <div className="grid grid-cols-[2fr_1fr_1fr_1fr] bg-slate-800 text-slate-300 text-xs font-semibold px-4 py-2">
          <span>Bowler</span>
          <span className="text-center">O</span>
          <span className="text-center">R</span>
          <span className="text-center">W</span>
        </div>
        {card.bowling.map((line, i) => (
          <div
            key={line.name + i}
            className={`grid grid-cols-[2fr_1fr_1fr_1fr] px-4 py-2 text-sm ${i % 2 ? "bg-slate-900" : "bg-slate-900/50"}`}
          >
            <span>{line.name}</span>
            <span className="text-center">{line.overs}</span>
            <span className="text-center">{line.runs}</span>
            <span className="text-center font-semibold">{line.wickets}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

export default Scorecard;
