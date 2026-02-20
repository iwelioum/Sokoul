function infoSerieTV(n, e) {
    (e = Episodes.base_path + n + "/season/" + e + "?api_key=" + Episodes.tvapikey + Episodes.base_lang + Episodes.language + Episodes.base_inc + Episodes.language.slice(0, 2) + ",null"),
    jQuery.getJSON(e, function(o) {
        jQuery(".tv-details-episodes .range").empty();
        //jQuery(".server_container").empty();
        for (let a = 0; a < o.episodes.length; a++) {
            o.name, o.overview;
            var i = o.season_number;
            o.poster_path;
            let e = o.episodes[a].name;
            var t = o.episodes[a].episode_number;
            null == e && (e = Episodes.tvepisode + " " + t), o.episodes[a].overview, o.episodes[a].air_date, jQuery("#watch > div.container > div.watch-extra > div.bl-1 > section.info > div.poster > span").empty();
            let s = "<img data-season='season" + i + "' src='" + Episodes.base_poster + o.poster_path + "' alt='" + Episodes.tvtitle + "'>";
            s == "<img data-season='season1' src='" + Episodes.base_poster + o.poster_path + "' alt='" + Episodes.tvtitle + "'>" ? (s = "<img data-season='season1' src='" + Episodes.tvposter + "' alt='" + Episodes.tvtitle + "'>") : s == "<img data-season='season" + i + "' src='" + Episodes.base_poster_lost + "' alt='" + Episodes.tvtitle + "'>" && (s = "<img data-season='season" + i + "' src='" + Episodes.base_poster_null + "' alt='" + Episodes.tvtitle + "'>"),
                jQuery("#watch > div.container > div.watch-extra > div.bl-1 > section.info > div.poster > span").append(s), "" === e && (e = Episodes.tvepisode + "&nbsp;" + t),
                jQuery(".tv-details-episodes .range").append("<div class='episode'><a href='javascript:void(0)' data-episode_id='" + t + "' onclick='infoEpisodio(" + n + "," + i + "," + o.episodes[a].episode_number + ")'><span>Episode " + o.episodes[a].episode_number + "</span>&nbsp;&nbsp;<span class='name'>" + o.episodes[a].name + "</span></a></div>"),
                jQuery(".tv-details-episodes a").on("click", function(e) {
                    e.preventDefault(), jQuery(".tv-details-episodes a").removeClass("active"), jQuery(this).addClass("active");
                }),
                jQuery('[data-episode_id="1"]').addClass("active"),
                jQuery("[data-load-embed]").attr("data-load-season", o.season_number);
                jQuery(".poster > span > img").attr("src", Episodes.base_poster + o.poster_path);
        }
    });
}

function imgError(e) {
    return (e.onerror = ""), (e.src = Episodes.placeholder), !0;
}

function infoEpisodio(e, i, t) {
    var sorry = Episodes.novideo;
    (e = Episodes.base_path + e + "/season/" + i + "/episode/" + t + "?api_key=" + Episodes.tvapikey + Episodes.base_lang + Episodes.language + Episodes.base_inc + Episodes.language.slice(0, 2) + ",null"),
    jQuery.getJSON(e, function(e) {
        e.overview;
        var s = e.air_date;
        let a = Episodes.base_backdrop + e.still_path;
        let overview = e.overview;
        Episodes.base_backdrop_null === a && (a = Episodes.placeholder), null == e.name && (e.name = "");
        var o = e.name,
            e = e.episode_number;
        "" === o && Episodes.tvepisode,
            jQuery(".player").empty(),
            jQuery(".desc.shorting").empty(),
            jQuery(".server_container").empty(),
            jQuery("#seasons > button").empty(),
            jQuery("#seasons > button").append("<i class='fa fa-list'></i> <span class='value'>Season " + i + " - <span class='date'>" + s + "</span></span>&nbsp;<i class='fa fa-caret-down'></i>"),
            jQuery(".play").css("background-image", "url(" + a + ")"),
            jQuery("[data-load-embed]").attr("data-load-season", i),
            jQuery("[data-load-embed]").attr("data-load-episode", t),
            jQuery(".server_container").attr("id", "s" + i + "_" + t),
        jQuery('[data-server="1"]').attr("id", "s" + i + "_" + t + "_1");
        jQuery('[data-server="2"]').attr("id", "s" + i + "_" + t + "_2");
        jQuery('[data-server="3"]').attr("id", "s" + i + "_" + t + "_3");
        jQuery('[data-server="4"]').attr("id", "s" + i + "_" + t + "_4");
        var server = "s" + i + "_" + t;
        var l = links[server];
        var url_4 = "https://www.2embed.to/embed/tmdb/tv?id=" + Episodes.tvid + "&s=" + i + "&e=" + t;
        var server_4 = "2embed";
        if (typeof l === 'undefined') {
            console.log('data is empty');
            jQuery(".server_container#s" + i + "_" + t).append('<center><div class="no-server">No servers!</div></center>');
            jQuery("#iframe").attr("src", sorry);
            jQuery(".note").html('We are sorry but no server is available');
        } else {
            var length = l.data[0];
            console.log('data is not empty');
            jQuery(".note").html('If current server doesn\'t work please try other servers below.');
            var url_1 = length[1].url;
            var url_2 = length[2].url;
            var url_3 = length[3].url;
            var server_1 = length[1].server;
            var server_2 = length[2].server;
            var server_3 = length[3].server;
            if (url_1 === "") {
                var itemp1 = "";
            } else {
                itemp1 = '<div data-server="1" class="server active" data-load-embed-host="' + server_1 + '" data-load-embed="' + Episodes.tvid + '" data-load-link="' + url_1 + '" data-load-season="' + i + '" data-load-episode="' + t + '">' + "<span>Server</span>" + '<div class="server_name">' + server_1 + "</div>" + "</div>";
                console.log(url_1);
            }
            if (url_2 === "") {
                var itemp2 = "";
            } else {
                itemp2 = '<div data-server="2" class="server" data-load-embed-host="' + server_2 + '" data-load-embed="' + Episodes.tvid + '" data-load-link="' + url_2 + '" data-load-season="' + i + '" data-load-episode="' + t + '">' + "<span>Server</span>" + '<div class="server_name">' + server_2 + "</div>" + "</div>";
                console.log(url_2);
            }
            if (url_2 === "") {
                var itemp3 = "";
            } else {
                itemp3 = '<div data-server="3" class="server" data-load-embed-host="' + server_3 + '" data-load-embed="' + Episodes.tvid + '" data-load-link="' + url_3 + '" data-load-season="' + i + '" data-load-episode="' + t + '">' + "<span>Server</span>" + '<div class="server_name">' + server_3 + "</div>" + "</div>";
                console.log(url_3);
            }
            jQuery(".server_container#s" + i + "_" + t).append(itemp1 + itemp2 + itemp3);
        }
        jQuery(".server.active").trigger("click");
        jQuery(".desc.shorting").append(overview);
    });
}
if (
    (jQuery(function() {
        var e = Episodes.tvapikey + Episodes.base_lang + Episodes.language + Episodes.base_inc + Episodes.language.slice(0, 2) + ",null",
            t = Episodes.tvid;
        document.getElementById("image"),
            new Event("error"),
            (e = Episodes.base_path + t + "?api_key=" + e),
            jQuery.getJSON(e, function(s) {
                s.overview;
                var e = (s.seasons.length, s.number_of_episodes),
                    a = s.status;
                let o = s.tagline;
                var i = s.original_name;
                "" === o && (o = i), null === o && (o = i), jQuery(".tagline").append(o), jQuery(".stato").text(a), jQuery(".epinum i").append(e), jQuery("#seasons > ul").empty();
                for (let e = 0; e < s.seasons.length; e++) jQuery("#seasons > ul").append("<li onclick='infoSerieTV(" + t + "," + s.seasons[e].season_number + ");infoEpisodio(" + t + "," + s.seasons[e].season_number + ",1);' data-tab='season" + s.seasons[e].season_number + "'><span>" + Episodes.tvseason + " " + s.seasons[e].season_number + "</span></li>");
                jQuery("#seasons > ul li").on("click", function(e) {
                        e.preventDefault(), jQuery("#seasons > ul li").removeClass("active"), jQuery(this).addClass("active");
                    }),
                    jQuery('#seasons > ul li[data-tab="season1"]').addClass("active"),
                    jQuery('#seasons > ul li[data-tab="season0"]').hide();
            });
    }), jQuery("#play").click(function(e) {
        e.preventDefault(),
            setTimeout(function() {
                jQuery("#iframe").show();
            }, 1200),
            jQuery("#play").hide();
    }), jQuery(window).on("load", function() {
        infoSerieTV(Episodes.tvid, 1), infoEpisodio(Episodes.tvid, 1, 1);
        jQuery('#seasons > ul li[data-tab="season0"]').hide();
    }), jQuery(document).on("click", "[data-load-embed]", function() {
        var e = jQuery(this).attr("data-load-link"),
            s = jQuery(this).attr("data-load-season"),
            a = jQuery(this).attr("data-load-episode");
    jQuery("[data-load-embed]").removeClass("active"), jQuery(this).addClass("active"), jQuery("#iframe").attr("src", e);
    }), void 0 !== Episodes.vote_average)) {
    const E = parseFloat(Episodes.vote_average),
        F = rateToStars(E);
    jQuery(".stars").append(F);
}


