import { CloudG, Footscape, BeanstalkScene, HoodScene, Logo } from "./art.jsx";

// Soft sky, drifting clouds and rolling hills behind every page.
export function Backdrop({ children }) {
  return (
    <div className="backdrop">
      <svg className="sky-clouds" viewBox="0 0 1200 400" preserveAspectRatio="xMidYMin slice" aria-hidden="true">
        <g className="drift drift-a"><CloudG x={160} y={90} s={1.1} /></g>
        <g className="drift drift-b"><CloudG x={620} y={60} s={0.8} /></g>
        <g className="drift drift-c"><CloudG x={1020} y={130} s={1.2} /></g>
      </svg>
      {children}
      <Footscape />
    </div>
  );
}

export function Brand({ big = false }) {
  return (
    <span className={big ? "brand big" : "brand"}>
      <Logo size={big ? 46 : 34} />
      <span className="brand-name">Once Upon a Comic</span>
    </span>
  );
}

// Layout for the password page and the Google sign-in page: a storybook "door" card between two scenes.
export function GateLayout({ children }) {
  return (
    <Backdrop>
      <div className="gate-wrap">
        <BeanstalkScene className="gate-art gate-art-left" />
        <main className="gate-card">{children}</main>
        <HoodScene className="gate-art gate-art-right" />
      </div>
    </Backdrop>
  );
}
