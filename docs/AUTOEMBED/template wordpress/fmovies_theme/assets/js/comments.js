var disqus_observer = new IntersectionObserver(
    function (e) {
        var s;
        e[0].isIntersecting &&
            ((s = document), ((e = s.createElement("script")).src = "https://" + comments + ".disqus.com/embed.js"), e.setAttribute("data-timestamp", +new Date()), (s.head || s.body).appendChild(e), disqus_observer.disconnect());
    },
    { threshold: [0] }
);
disqus_observer.observe(document.querySelector("#disqus_thread"));
