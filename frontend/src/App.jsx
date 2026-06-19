import { BrowserRouter, Routes, Route } from "react-router-dom";
import Navbar from "./components/Navbar";
import Home from "./pages/Home";
import Draft from "./pages/Draft";
import Result from "./pages/Result";
import Scorecard from "./pages/Scorecard";
import "./App.css";

function App() {
  return (
    <BrowserRouter>
      <Navbar />
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/draft/:mode" element={<Draft />} />
        <Route path="/result" element={<Result />} />
        <Route path="/scorecard/:matchId" element={<Scorecard />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
