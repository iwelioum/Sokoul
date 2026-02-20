jQuery(document).ready(function ($) {
    // Your existing code here
});

function vote_submit(videoId, voteValue) {
    // Your logic for submitting the vote
    console.log('Vote submitted for', videoId, 'with value', voteValue);

    // Example AJAX request
    $.ajax({
        url: '/wp-content/themes/fmovie/uploads/submit-vote.php', // Replace with your server-side endpoint for submitting votes
        method: 'POST',
        data: {
            videoId: videoId,
            voteValue: voteValue
        },
        success: function(response) {
            console.log('Vote submitted successfully:', response);
            // Add any additional logic based on the server response
        },
        error: function(error) {
            console.error('Error submitting vote:', error);
            // Handle errors appropriately
        }
    });
}

// Like function
function like(videoId) {
    vote_submit(videoId, 1); // 1 represents like
}

// Dislike function
function dislike(videoId) {
    vote_submit(videoId, 0); // 0 represents dislike
}
function rateToStars(rate) {
    if (!rate)
        return [
            '<span class="fa fa-star"><i class="fa fa-star none"></i></span>',
            '<span class="fa fa-star"><i class="fa fa-star none"></i></span>',
            '<span class="fa fa-star"><i class="fa fa-star none"></i></span>',
            '<span class="fa fa-star"><i class="fa fa-star none"></i></span>',
            '<span class="fa fa-star"><i class="fa fa-star none"></i></span>',
        ].join("");

    var p_rating = Math.round(rate.toFixed(1)) / 2,
        stars = "";

    for (var i = 1; i <= Math.floor(p_rating); i++) {
        stars += '<span class="fa fa-star"><i class="fa fa-star"></i></span>';
    }
    if (p_rating % 1 > 0) {
        stars += '<span class="fa fa-star"><i class="fa fa-star-half"></i></span>';
    }

    for (var i = Math.ceil(p_rating); i < 5; i++) {
        stars += '<span class="fa fa-star"><i class="fa fa-star none"></i></span>';
    }

    return stars;
}

(function($) {
    const swiperContainer = document.querySelector("#slider");
    if (swiperContainer !== null) {
        const swiper = new Swiper(swiperContainer, {
            preloadImages: false,
            lazy: true,
            slidesPerView: 1,
            loop: true,
            grabCursor: true,
            pagination: {
                el: ".paging",
                clickable: true,
            },
            autoplay: {
                delay: 5000,
            },
        });
    }

    function ToolTips() {
        $(".filmlist .item").tooltipster({
            trigger: "hover",
            theme: "tooltipster-base",
            contentAsHTML: true,
            animation: "fade",
            updateAnimation: true,
            interactive: true,
            arrow: false,
            position: "right",
            maxWidth: 260,
            minWidth: 260,
            maxHeight: 252,
            minHeight: 252,
            delay: 350,
            plugins: ["sideTip"],
        });
    }
    $('a[rel*="null"]').click(function(e) {
        e.preventDefault();
    });
    $('a.bookmark').click(function(e) {
        e.preventDefault();
    });
    $("#menu-toggler").click(function() {
        $("#menu, #menu-header").toggle();
    });
    $("#search-toggler").click(function() {
        $("#search").toggle();
    });

    $("#menu li, #menu-header li").hover(
        function() {
            $(this).find(".genre").show();
            $(this).find(".country").show();
        },
        function() {
            $(this).find(".genre").hide();
            $(this).find(".country").hide();
        }
    );

    var genre = $(".genre");
    $(".clicky").on("click", function(e) {
        e.preventDefault();
        e.stopPropagation();
        genre.toggle();
    });
    $(document).on("click", function(e) {
        genre.hide();
    });

    var country = $(".country");
    $(".clicky2").on("click", function(e) {
        e.preventDefault();
        e.stopPropagation();
        country.toggle();
    });
    $(document).on("click", function(e) {
        country.hide();
    });

    $("[data-go='#comment']").click(function() {
        $("html, body").animate({
                scrollTop: $("#comment").offset().top,
            },
            2000
        );
    });

    $(".bp-btn-light").click(function(e) {
        e.preventDefault();
        $(".bp-btn-light, #overlay, #body, #watch, #media-player, #player, #content-embed, #disqus_thread").toggleClass("active");
        $([document.documentElement, document.body]).animate({
                scrollTop: $("#player").offset().top - 0,
            },
            1000
        );
    });

    $("#overlay").click(function() {
            $(".bp-btn-light, #overlay, #body, #watch, #media-player, #player, #disqus_thread").removeClass("active");
        }),
        $(".btn-eps").click(function(e) {
            e.preventDefault();
            $([document.documentElement, document.body]).animate({
                    scrollTop: $("#player").offset().top - 0,
                },
                1000
            );
        }),
        $(".bp-btn-auto").click(function() {
            $(".bp-btn-auto").toggleClass("active");
        }),
        $(".bp-btn-auto").click(function() {
            $(".bp-btn-auto").toggleClass("active");
        }),
        $("#toggle, .cac-close").click(function() {
            $("#comment").toggleClass("active");
        });

    $(".servers").on("click", "li", function() {
        $(".servers li.active").removeClass("active");
        $(this).addClass("active");
    });
    $(".seasons").on("click", "li", function() {
        $(".seasons li.active").removeClass("active");
        $(this).addClass("active");
    });

    function TabFavs() {
        const g = document.querySelectorAll("#controls .bookmark, .item .bookmark");
        if (null != typeof g && 0 < g.length) {
            function c(b, c) {
                b ? $(c).attr("data-bookmark", "Remove").text("Remove") : $(c).attr("data-bookmark", "Favorite").text("Favorite");
            }
            let d;
            for (localStorage.hasOwnProperty("favorite_movies") ? (d = JSON.parse(localStorage.favorite_movies)) : (localStorage.setItem("favorite_movies", "[]"), (d = [])), i = 0; i < g.length; i++) {
                const $ = g[i],
                    b = $.id,
                    f = $.classList;
                d.includes(b) ? (f.remove("inactive"), f.add("active"), c(!0, $)) : c(!1, $),
                    $.addEventListener("click", function() {
                        f.toggle("inactive"),
                            f.toggle("active"),
                            d.includes(b) ?
                            ((d = d.filter(function(c) {
                                    return c !== b;
                                })),
                                c(!1, $)) :
                            (d.push(b), c(!0, $)),
                            (localStorage.favorite_movies = JSON.stringify(d));
                    });
            }
        }
    }

    $(".tab").on("click", function(e) {
        e.preventDefault();
        var filter = $(".tab");
        filter.removeClass("active");
        $(this).addClass("active");
        var filter_active = $(".tab.active i");
        $.ajax({
            type: "POST",
            url: ajax_url,
            dataType: "html",
            async: false,
            cache: true,
            data: {
                action: "filter_posts",
                category: $(this).data("slug"),
                orderby: $(this).data("orderby"),
                exclude: $(this).data("exclude"),
                meta: $(this).data("meta"),
            },
            beforeSend: function(xhr) {
                filter_active.addClass("fa-spin");
                setTimeout(function() {
                    filter_active.removeClass("fa-spin");
                }, 3000);
            },
            success: function(res) {
                $(".filmlist.no").html(res + '<div class="clearfix"></div>');
                ToolTips();
                TabFavs();
                lazyload();
            },
        });
    });
    var box = jQuery(".slide-read-more");
    var minimumHeight = 43; 
    var initialHeight = box.innerHeight();
    if (initialHeight > minimumHeight) {
        box.css('height', minimumHeight);
        jQuery(".read-more-button").show();
    }
    SliderReadMore();
    function SliderReadMore() {
        jQuery(".slide-read-more-button").on('click', function() {
            var currentHeight = box.innerHeight();
            var autoHeight = box.css('height', 'auto').innerHeight();
            var newHeight = (currentHeight | 0) === (autoHeight | 0) ? minimumHeight : autoHeight;

            box.css('height', currentHeight).animate({
                height: (newHeight)
            })
            jQuery(".slide-read-more-button").toggle();
        });
    }
    ToolTips();
    lazyload();
})(jQuery);

