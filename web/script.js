const statNumbers = document.querySelectorAll('.stat-number');
const animationDuration = 2000;

const animateStats = (entry) => {
  if (!entry.isIntersecting) return;

  statNumbers.forEach((stat) => {
    const target = Number(stat.dataset.target);
    let start = 0;
    const startTime = performance.now();

    const update = (time) => {
      const progress = Math.min((time - startTime) / animationDuration, 1);
      const currentValue = Math.floor(progress * target + start);
      stat.textContent = target >= 100 ? currentValue : `${currentValue}%`;
      if (progress < 1) requestAnimationFrame(update);
    };

    requestAnimationFrame(update);
  });

  statObserver.disconnect();
};

const statObserver = new IntersectionObserver((entries) => {
  entries.forEach(animateStats);
}, { threshold: 0.3 });

statNumbers.forEach((stat) => statObserver.observe(stat));

document.getElementById('year').textContent = new Date().getFullYear();

const nav = document.querySelector('.nav');
let lastScrollY = 0;

window.addEventListener('scroll', () => {
  const currentScroll = window.scrollY;
  if (currentScroll > lastScrollY && currentScroll > 120) {
    nav.classList.add('nav--hidden');
  } else {
    nav.classList.remove('nav--hidden');
  }
  lastScrollY = currentScroll;
});
