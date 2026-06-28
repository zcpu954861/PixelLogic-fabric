import './styles.css';

type FlowCard = {
  title: string;
  body: string;
};

const cards: FlowCard[] = [
  {
    title: 'Trigger',
    body: 'A server event starts the graph.',
  },
  {
    title: 'Condition',
    body: 'Branches stay visible as their own cards.',
  },
  {
    title: 'Action',
    body: 'Typed fields keep actions predictable.',
  },
];

const app = document.querySelector<HTMLDivElement>('#app');

if (app) {
  app.innerHTML = `
    <section class="shell" aria-labelledby="title">
      <header class="masthead">
        <p class="eyebrow">Graph / Node / Edge first</p>
        <h1 id="title">PixelLogic</h1>
        <p class="summary">
          Visual logic flows for Minecraft servers, built around readable cards and direct connections.
        </p>
      </header>
      <section class="canvas" aria-label="Flow preview">
        ${cards
          .map(
            (card, index) => `
              <article class="node" style="--i: ${index}">
                <span class="node-index">0${index + 1}</span>
                <h2>${card.title}</h2>
                <p>${card.body}</p>
              </article>
            `,
          )
          .join('')}
      </section>
      <footer class="notes">
        <span>Channel hidden by default</span>
        <span>Human-centered card interaction</span>
      </footer>
    </section>
  `;
}
