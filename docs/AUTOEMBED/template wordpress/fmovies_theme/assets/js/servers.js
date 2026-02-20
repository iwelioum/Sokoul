let d, e;
const premium = Servers.premium,
    embedru = Servers.embedru,
    superembed = Servers.superembed,
    svetacdn = Servers.svetacdn,
    openvids = Servers.openvids;
    vidsrc = Servers.vidsrc;
    video = Servers.embed + "&video=true";
function loadServer(e) {
    jQuery("#iframe").attr("src", e);
}
function loadEmbed(e) {
    jQuery("#iframe").attr("src", e);
}
jQuery(".server").click(function (e) {
    e.preventDefault(), jQuery(".server").removeClass("active"), jQuery(this).addClass("active");
}),
    jQuery("#play").click(function (e) {
        e.preventDefault(),
            setTimeout(function () {
                jQuery("#iframe").show();
            }, 1000),
            jQuery("#play").hide(),
            jQuery(".server.active").trigger("click");
    }),
    void 0 !== Servers.vote_average && ((d = parseFloat(Servers.vote_average)), (e = rateToStars(d)), jQuery(".stars").append(e)),
    jQuery("#manual:first").addClass("active");
