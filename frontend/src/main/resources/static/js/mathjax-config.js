// Must load before MathJax itself. The server writes every formula as \( \) (inline) or \[ \] (display), so
// "$" is deliberately not a delimiter: a plain dollar sign in a post ("costs $5") stays a dollar sign.
window.MathJax = {
  tex: {
    inlineMath: [['\\(', '\\)']],
    displayMath: [['\\[', '\\]']],
    processEscapes: true,
    tags: 'ams'
  },
  svg: { fontCache: 'global' }
};
