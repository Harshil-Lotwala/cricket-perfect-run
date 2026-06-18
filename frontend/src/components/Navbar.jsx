import { Link } from "react-router-dom";

function Navbar() {
  return (
    <nav>
      <Link to="/">Cricket Perfect Run</Link>
      <Link to="/draft">Draft</Link>
      <Link to="/leaderboard">Leaderboard</Link>
    </nav>
  );
}

export default Navbar;