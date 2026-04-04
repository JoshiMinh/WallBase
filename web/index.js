document.addEventListener('DOMContentLoaded', () => {
  // Intersection Observer for scroll reveal animations
  const observerOptions = {
    threshold: 0.1,
    rootMargin: '0px 0px -50px 0px'
  };

  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add('active');
        observer.unobserve(entry.target);
      }
    });
  }, observerOptions);

  // Apply highlight to sections with the reveal class
  const revealElements = document.querySelectorAll('.reveal');
  revealElements.forEach(el => {
    observer.observe(el);
  });

  // Smooth hover effect for mockups (subtle 3D tilt)
  const mockups = document.querySelectorAll('.mockup-wrapper');
  mockups.forEach(mockup => {
    mockup.addEventListener('mousemove', (e) => {
      const rect = mockup.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;
      
      const centerX = rect.width / 2;
      const centerY = rect.height / 2;
      
      const rotateX = (y - centerY) / 30;
      const rotateY = (centerX - x) / 30;
      
      mockup.style.transform = `perspective(1000px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) translateY(-10px)`;
    });
    
    mockup.addEventListener('mouseleave', () => {
      mockup.style.transform = `perspective(1000px) rotateX(0deg) rotateY(0deg) translateY(0)`;
    });
  });

  console.log('WallBase Redesign initialized with root-integrated assets ✨');
});
