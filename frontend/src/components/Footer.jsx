const links = [
  { label: "GitHub ↗", href: "https://github.com/Harshil-Lotwala" },
  { label: "LinkedIn ↗", href: "https://www.linkedin.com/in/harshil-lotwala/" },
  { label: "Email ↗", href: "mailto:hashilv3034@gmail.com" },
];

function Footer() {
  return (
    <footer className="relative overflow-hidden border-t border-slate-800 bg-[#080d10] text-sm text-slate-400">
      <div className="h-1 bg-gradient-to-r from-lime-300 via-sky-400 to-fuchsia-500" />
      <div className="mx-auto grid w-full max-w-[1500px] gap-8 px-5 py-10 sm:px-8 lg:grid-cols-[1.15fr_.85fr] lg:items-end lg:px-12">
        <div className="max-w-xl">
          <p className="eyebrow mb-3">FOUR FORMATS · ONE PERFECT RUN</p>
          <div className="mb-3 text-white">
            <span className="text-lg font-black tracking-tight">PERFECT RUN</span>
          </div>
          <p className="text-base leading-7 text-slate-300">
            Draft history, choose your leaders, and find out whether your XI can survive the season.
          </p>
        </div>

        <div className="lg:text-right">
          <nav aria-label="Creator links" className="mb-5 flex flex-wrap gap-2 lg:justify-end">
            {links.map((link) => (
              <a
                key={link.label}
                href={link.href}
                target={link.href.startsWith("http") ? "_blank" : undefined}
                rel={link.href.startsWith("http") ? "noreferrer" : undefined}
                className="border border-slate-700 bg-slate-900 px-4 py-2 font-bold text-slate-300 transition-colors hover:border-lime-300 hover:bg-lime-300/5 hover:text-lime-300"
              >
                {link.label}
              </a>
            ))}
          </nav>
          <div className="flex flex-col gap-2 border-t border-slate-800 pt-4 sm:flex-row sm:items-center sm:justify-between lg:justify-end lg:gap-8">
            <p className="text-xs text-slate-600">© 2026 Harshil Lotwala. Built for cricket obsessives.</p>
            <a href="#top" className="w-fit font-bold text-slate-300 transition-colors hover:text-lime-300">
              Back to top ↑
            </a>
          </div>
        </div>
      </div>
    </footer>
  );
}

export default Footer;
