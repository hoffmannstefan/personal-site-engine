(function () {
  'use strict';

  var section = document.getElementById('comments');
  if (!section) return;

  var slug = section.dataset.slug;
  var api = '/api/posts/' + encodeURIComponent(slug) + '/comments';
  var list = document.getElementById('comment-list');
  var empty = document.getElementById('comments-empty');
  var count = document.getElementById('comments-count');
  var form = document.getElementById('comment-form');
  var authorInput = document.getElementById('comment-author');
  var bodyInput = document.getElementById('comment-body');
  var charCount = document.getElementById('char-count');
  var status = document.getElementById('comment-status');
  var submit = document.getElementById('comment-submit');
  var banner = document.getElementById('reply-banner');
  var replyTo = document.getElementById('reply-to');
  var replyParent = null;

  try {
    authorInput.value = localStorage.getItem('comment-author') || '';
  } catch (e) { /* storage unavailable: fine */ }

  // All user content is written with textContent, never innerHTML.
  function el(tag, className, text) {
    var node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
  }

  function formatDate(iso) {
    var d = new Date(iso);
    return d.toLocaleDateString('en', { year: 'numeric', month: 'long', day: 'numeric' }) +
      ', ' + d.toLocaleTimeString('en', { hour: '2-digit', minute: '2-digit' });
  }

  function renderComment(c, canReply) {
    var li = el('li', 'comment');
    li.id = 'comment-' + c.id;
    var head = el('div', 'comment-head');
    head.appendChild(el('strong', 'comment-author', c.author));
    var time = el('time', 'comment-date', formatDate(c.createdAt));
    time.dateTime = c.createdAt;
    head.appendChild(time);
    li.appendChild(head);
    li.appendChild(el('p', 'comment-body', c.body));
    if (canReply && c.id != null) {
      var reply = el('button', 'link-button', 'Reply');
      reply.type = 'button';
      reply.addEventListener('click', function () { startReply(c); });
      li.appendChild(reply);
    }
    return li;
  }

  function render(comments) {
    var replies = {};
    comments.forEach(function (c) {
      if (c.parentId != null) (replies[c.parentId] = replies[c.parentId] || []).push(c);
    });
    list.textContent = '';
    comments.forEach(function (c) {
      if (c.parentId != null) return;
      var li = renderComment(c, true);
      if (replies[c.id]) {
        var sub = el('ol', 'comment-replies');
        replies[c.id].forEach(function (r) { sub.appendChild(renderComment(r, true)); });
        li.appendChild(sub);
      }
      list.appendChild(li);
    });
    count.textContent = comments.length ? '(' + comments.length + ')' : '';
    empty.hidden = comments.length > 0;
  }

  function startReply(c) {
    replyParent = c.id;
    replyTo.textContent = c.author;
    banner.hidden = false;
    form.scrollIntoView({ behavior: 'smooth', block: 'center' });
    bodyInput.focus();
  }

  function cancelReply() {
    replyParent = null;
    banner.hidden = true;
  }
  document.getElementById('reply-cancel').addEventListener('click', cancelReply);

  function setStatus(message, isError) {
    status.textContent = message;
    status.className = 'comment-status' + (isError ? ' error' : '');
  }

  function load() {
    return fetch(api, { headers: { Accept: 'application/json' } })
      .then(function (r) {
        if (!r.ok) throw new Error('HTTP ' + r.status);
        return r.json();
      })
      .then(render)
      .catch(function () {
        empty.hidden = false;
        empty.textContent = 'Comments could not be loaded right now.';
      });
  }

  bodyInput.addEventListener('input', function () {
    charCount.textContent = bodyInput.value.length;
  });

  form.addEventListener('submit', function (event) {
    event.preventDefault();
    var author = authorInput.value.trim();
    var body = bodyInput.value.trim();
    if (!author || !body) {
      setStatus('Please enter your name and a comment.', true);
      return;
    }
    submit.disabled = true;
    setStatus('Posting…', false);
    fetch(api, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({
        author: author,
        body: body,
        parentId: replyParent,
        website: document.getElementById('comment-website').value
      })
    })
      .then(function (r) {
        if (r.ok) return r.json();
        return r.json().catch(function () { return {}; }).then(function (p) {
          throw new Error(p.detail || p.message || 'Could not post your comment.');
        });
      })
      .then(function (created) {
        try { localStorage.setItem('comment-author', author); } catch (e) { /* ignore */ }
        bodyInput.value = '';
        charCount.textContent = '0';
        cancelReply();
        setStatus('Thanks! Your comment was posted.', false);
        return load().then(function () {
          var node = created.id != null && document.getElementById('comment-' + created.id);
          if (node) node.scrollIntoView({ behavior: 'smooth', block: 'center' });
        });
      })
      .catch(function (err) { setStatus(err.message, true); })
      .then(function () { submit.disabled = false; });
  });

  load();
})();
