import { lazy, Suspense } from "react";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import Navbar from "./components/Navbar";
import Footer from "./components/Footer";
import "./App.css";

const Home = lazy(() => import("./pages/Home"));
const Draft = lazy(() => import("./pages/Draft"));
const Result = lazy(() => import("./pages/Result"));
const Scorecard = lazy(() => import("./pages/Scorecard"));
const Leaderboard = lazy(() => import("./pages/Leaderboard"));

function App() {
  return (
    <BrowserRouter basename={import.meta.env.BASE_URL}>
      <div id="top" className="min-h-screen flex flex-col">
        <Navbar />
        <main className="flex-1">
          <Suspense fallback={<div className="min-h-[60vh] grid place-items-center text-lime-300 font-black">LOADING RUN…</div>}>
            <Routes>
              <Route path="/" element={<Home />} />
              <Route path="/draft/:mode" element={<Draft />} />
              <Route path="/result" element={<Result />} />
              <Route path="/scorecard/:matchId" element={<Scorecard />} />
              <Route path="/leaderboard/:mode?" element={<Leaderboard />} />
            </Routes>
          </Suspense>
        </main>
        <Footer />
      </div>
    </BrowserRouter>
  );
}

export default App;
