import { Link } from "react-router-dom";

function Navbar() {
  return (
    <nav className="bg-slate-950/80 backdrop-blur border-b border-slate-800 sticky top-0 z-10">
      <div className="max-w-7xl mx-auto px-6 py-4 flex items-center gap-6">
        <Link to="/" className="font-black text-lg">
          🏏 Cricket Perfect Run
        </Link>
        <div className="flex gap-4 text-sm text-slate-300">
          <Link to="/" className="hover:text-white">
            Home
          </Link>
          <Link to="/draft/ipl" className="hover:text-white">
            Draft
          </Link>
          <Link to="/result" className="hover:text-white">
            Result
          </Link>
        </div>
      </div>
    </nav>
  );
}

export default Navbar;
