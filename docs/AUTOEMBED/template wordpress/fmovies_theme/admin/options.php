<?php
/* Exit if accessed directly */
if ( ! defined( 'ABSPATH' ) ) {
    exit;
}
$full_url =  esc_url( home_url() );
$array = parse_url($full_url);
/*====================================*\
    FUNCTIONS
\*====================================*/
function admin_options($option) {
        $option('admin_color_style', 'green');
        $option('admin_google_fonts', 'roboto');
    $option('admin_apikey', '');
    $option('admin_apilanguage', '');
    $option('admin_disqus', '');
    $option('admin_omdb', '');
    $option('admin_multiplayer', '');
    $option('admin_multiserver', '');
    $option('admin_tvdl', '');
    $option('admin_switch', '');
    $option('admin_enabletv', '');
    $option('admin_enabletag', '');
    $option('admin_sortby', '');
    $option('admin_quality', '');
    $option('admin_share', '');
    $option('admin_letter', '');
    $option('admin_postviews', '');
    $option('admin_likes', '');
    $option('admin_similar', '');
    $option('admin_favorites', '');
    $option('admin_minify', '');
    $option('admin_scroll', '');
    $option('admin_full', '');
    $option('admin_grid', '');
    $option('admin_seo', '');
    $option('admin_advertise', '');
    //$option('admin_email', '');
    $option('admin_recently', '');
    $option('admin_mostrated', '');
    $option('admin_mostwatched', '');
    $option('admin_testolike', '');
    $option('admin_releasedate', '');
    $option('admin_titleato', '');
    $option('admin_fullbio', '');
    $option('admin_nobio', '');
    $option('admin_monetize', '');
    $option('admin_language', '');
    $option('admin_txtcomments', '');
    $option('admin_sharebutton', '');
    $option('admin_topicon', '');
    $option('admin_randomicon', '');
    $option('admin_description', '');
    $option('admin_description_tv', '');
    $option('admin_slogan', '');
    $option('admin_deschome', '');
    $option('admin_latest', '');
    $option('admin_recommended', '');
    $option('admin_trending', '');
    $option('admin_txtmovies', '');
    $option('admin_intheaters', '');
    $option('admin_top', '');
    $option('admin_random', '');
    $option('admin_tvseries', '');
    $option('admin_genre', '');
    $option('admin_year', '');
    $option('admin_country', '');
    $option('admin_search', '');
    $option('admin_network', '');
    $option('admin_creator', '');
    $option('admin_stars', '');
    $option('admin_season', '');
    $option('admin_seasons', '');
    $option('admin_episode', '');
    $option('admin_episodes', '');
    $option('admin_director', '');
    $option('admin_play', '');
    $option('admin_trailer', '');
    $option('admin_streaming', '');
    $option('admin_watch', '');
    $option('admin_download', '');
    $option('admin_textautoembed', '');
    $option('admin_textmultiserver', '');
    $option('admin_textfavorites', '');
    $option('admin_txtnoletter', '');
    $option('admin_related', '');
    $option('admin_textlatest', '');
        $option('admin_textviewall', '');
    $option('admin_header_code', '');
    $option('admin_adbutton', 2);
    $option('admin_footer_showlinks', 1);
    $option('admin_footer_copyright', '');
    $option('admin_site_logo', '');
    $option('admin_site_favicon', '');
    $option('admin_header_soc_icons', 2);
    $option('admin_customizer', 2);
    $option('admin_url_facebook', '');
    $option('admin_url_twitter', '');
    $option('admin_url_instagram', '');
    $option('admin_comments', 2);
    $option('admin_rewrite', 2);
    $option('admin_premium', 2);
    $option('admin_latest_series', 1);
    $option('admin_latest_movies', 1);
    $option('admin_slider', 2);
    $option('admin_login', 2);
    $option('admin_reccomended', 2);
    $option('admin_related_post', 2);
    $option('admin_genre_link', 2);
    $option('admin_country_link', 2);
    $option('admin_top_imdb', 2);
    $option('admin_favorites_link', 2);
    $option('admin_footer_banner', 2);
    $option('admin_banner_position_1', '');
    $option('admin_sponsor', 2);
    $option('admin_sponsor1', '');
    $option('admin_sponsor2', '');
    $option('admin_button1', '');
    $option('admin_button2', '');
    $option('admin_server_0_text', '');
    $option('admin_server_1_text', '');
    $option('admin_server_2_text', '');
    $option('admin_server_3_text', '');
    $option('admin_server_2', 1);
    $option('admin_server_3', 1);
    $option('admin_btn_more', '');
    $option('admin_btn_less', '');
}
/*====================================*\
    GENERAL
\*====================================*/
$fmovie_apikey = get_option('admin_apikey');
$fmovie_apilanguage = get_option('admin_apilanguage');
$fmovie_omdb = get_option('admin_omdb');
$fmovie_disqus = get_option('admin_disqus');
//$fmovie_email = get_option('admin_email');
$fmovie_report = get_option('admin_report');
/*====================================*\
    TRANSLATE
\*====================================*/
$fmovie_slogan = get_option('admin_slogan');
$fmovie_deschome = get_option('admin_deschome');
$fmovie_latest = get_option('admin_latest');
$fmovie_recommended = get_option('admin_recommended');
$fmovie_trending = get_option('admin_trending');
$fmovie_txtmovies = get_option('admin_txtmovies');
$fmovie_intheaters = get_option('admin_intheaters');
$fmovie_top = get_option('admin_top');
$fmovie_random = get_option('admin_random');
$fmovie_tvseries = get_option('admin_tvseries');
$fmovie_contactus = get_option('admin_contactus');
$fmovie_genre = get_option('admin_genre');
$fmovie_year = get_option('admin_year');
$fmovie_country = get_option('admin_country');
$fmovie_search = get_option('admin_search');
$fmovie_network = get_option('admin_network');
$fmovie_creator = get_option('admin_creator');
$fmovie_stars = get_option('admin_stars');
$fmovie_season = get_option('admin_season');
$fmovie_seasons = get_option('admin_seasons');
$fmovie_episode = get_option('admin_episode');
$fmovie_episodes = get_option('admin_episodes');
$fmovie_director = get_option('admin_director');
$fmovie_play = get_option('admin_play');
$fmovie_share = get_option('admin_share');
$fmovie_trailer = get_option('admin_trailer');
$fmovie_streaming = get_option('admin_streaming');
$fmovie_watch = get_option('admin_watch');
$fmovie_download = get_option('admin_download');
$fmovie_recently = get_option('admin_recently');
$fmovie_mostrated = get_option('admin_mostrated');
$fmovie_mostwatched = get_option('admin_mostwatched');
$fmovie_testolike = get_option('admin_testolike');
$fmovie_testofav = get_option('admin_testofav');
$fmovie_releasedate = get_option('admin_releasedate');
$fmovie_titleato = get_option('admin_titleato');
$fmovie_fullbio = get_option('admin_fullbio');
$fmovie_nobio = get_option('admin_nobio');
$fmovie_textautoembed = get_option('admin_textautoembed');
$fmovie_textmultiserver = get_option('admin_textmultiserver');
$fmovie_textfavorites = get_option('admin_textfavorites');
$fmovie_txtnoletter = get_option('admin_txtnoletter');
$fmovie_related = get_option('admin_related');
$fmovie_advertise = get_option('admin_advertise');
$fmovie_monetize = get_option('admin_monetize');
$fmovie_language = get_option('admin_language');
$fmovie_txtquality = get_option('admin_txtquality');
$fmovie_txtcomments = get_option('admin_txtcomments');
$fmovie_button1 = get_option('admin_button1');
$fmovie_button2 = get_option('admin_button2');
$fmovie_textlatest = get_option('admin_textlatest');
$fmovie_textviewall = get_option('admin_textviewall');
// more buttons
$fmovie_btn_more = get_option('admin_btn_more');
$fmovie_btn_less = get_option('admin_btn_less');
$fmovie_server_0_text = get_option('admin_server_0_text');
$fmovie_server_1_text = get_option('admin_server_1_text');
$fmovie_server_2_text = get_option('admin_server_2_text');
$fmovie_server_3_text = get_option('admin_server_3_text');
/*====================================*\
    ADVERTISING
\*====================================*/
$fmovie_sponsor1 = get_option('admin_sponsor1');
$fmovie_sponsor2 = get_option('admin_sponsor2');
/*====================================*\
    GENERAL
\*====================================*/
if (strlen($fmovie_apikey) > 0) {
define	('apikey', html_entity_decode(stripslashes_deep($fmovie_apikey), ENT_QUOTES));
 } else {
define	('apikey', '7ac6de5ca5060c7504e05da7b218a30c');
 }
if (strlen($fmovie_apilanguage) > 0) {
define	('apilanguage', html_entity_decode(stripslashes_deep($fmovie_apilanguage), ENT_QUOTES));
} else {
define	('apilanguage', 'en-US');
}
if (strlen($fmovie_disqus) > 0) {
define	('disqus', html_entity_decode(stripslashes_deep($fmovie_disqus), ENT_QUOTES));
 } else {
define	('disqus', 'movieapp-1');
}
if (strlen($fmovie_omdb) > 0) {
define	('omdb', html_entity_decode(stripslashes_deep($fmovie_omdb), ENT_QUOTES));
 } else {
define	('omdb', 'b9bd48a6');
}
/*====================================*\
    TRANSLATE
\*====================================*/
if (strlen($fmovie_season) > 0) {
define	('season', html_entity_decode(stripslashes_deep($fmovie_season), ENT_QUOTES));
 } else {
define	('season', 'Season');
 }
 if (strlen($fmovie_seasons) > 0) {
define	('seasons', html_entity_decode(stripslashes_deep($fmovie_seasons), ENT_QUOTES));
 } else {
define	('seasons', 'Seasons');
 }
    if (strlen($fmovie_episode) > 0) {
define	('episode', html_entity_decode(stripslashes_deep($fmovie_episode), ENT_QUOTES));
 } else {
define	('episode', 'Episode');
 }
    if (strlen($fmovie_episodes) > 0) {
define	('episodes', html_entity_decode(stripslashes_deep($fmovie_episodes), ENT_QUOTES));
 } else {
define	('episodes', 'Episodes');
 }
if (strlen($fmovie_latest) > 0) {
define	('latest', html_entity_decode(stripslashes_deep($fmovie_latest), ENT_QUOTES));
 } else {
define	('latest', 'Latest');
}
if (strlen($fmovie_recommended) > 0) {
define	('recommended', html_entity_decode(stripslashes_deep($fmovie_recommended), ENT_QUOTES));
 } else {
define	('recommended', 'Recommended');
}
if (strlen($fmovie_trending) > 0) {
define	('trending', html_entity_decode(stripslashes_deep($fmovie_trending), ENT_QUOTES));
 } else {
define	('trending', 'Trending');
}
if (strlen($fmovie_top) > 0) {
define	('top', html_entity_decode(stripslashes_deep($fmovie_top), ENT_QUOTES));
 } else {
define	('top', 'Top IMDb');
 }
if (strlen($fmovie_random) > 0) {
define	('random', html_entity_decode(stripslashes_deep($fmovie_random), ENT_QUOTES));
 } else {
define	('random', 'Random');
 }
if (strlen($fmovie_download) > 0) {
define	('download', html_entity_decode(stripslashes_deep($fmovie_download), ENT_QUOTES));
 } else {
define	('download', 'Download');
 }
if (strlen($fmovie_watch) > 0) {
define	('watch', html_entity_decode(stripslashes_deep($fmovie_watch), ENT_QUOTES));
 } else {
define	('watch', 'Watch Now');
 }
if (strlen($fmovie_genre) > 0) {
define	('genre', html_entity_decode(stripslashes_deep($fmovie_genre), ENT_QUOTES));
 } else {
define	('genre', 'Genre');
 }
if (strlen($fmovie_year) > 0) {
define	('year', html_entity_decode(stripslashes_deep($fmovie_year), ENT_QUOTES));
} else {
define	('year', 'Year');
}
if (strlen($fmovie_country) > 0) {
define	('country', html_entity_decode(stripslashes_deep($fmovie_country), ENT_QUOTES));
} else {
define	('country', 'Country');
}
if (strlen($fmovie_search) > 0) {
define	('search', html_entity_decode(stripslashes_deep($fmovie_search), ENT_QUOTES));
} else {
define	('search', 'Search...');
}
if (strlen($fmovie_network) > 0) {
define	('network', html_entity_decode(stripslashes_deep($fmovie_network), ENT_QUOTES));
} else {
define	('network', 'Network');
}
if (strlen($fmovie_creator) > 0) {
define	('creator', html_entity_decode(stripslashes_deep($fmovie_creator), ENT_QUOTES));
 } else {
define	('creator', 'Creator');
}
if (strlen($fmovie_director) > 0) {
define	('director', html_entity_decode(stripslashes_deep($fmovie_director), ENT_QUOTES));
 } else {
define	('director', 'Director');
}
if (strlen($fmovie_stars) > 0) {
define	('stars', html_entity_decode(stripslashes_deep($fmovie_stars), ENT_QUOTES));
 } else {
define	('stars', 'Stars');
}
if (strlen($fmovie_intheaters) > 0) {
define	('intheaters', html_entity_decode(stripslashes_deep($fmovie_intheaters), ENT_QUOTES));
 } else {
define	('intheaters', 'In Theaters');
 }
if (strlen($fmovie_txtmovies) > 0) {
define	('txtmovies', html_entity_decode(stripslashes_deep($fmovie_txtmovies), ENT_QUOTES));
 } else {
define	('txtmovies', 'Movies');
}
if (strlen($fmovie_tvseries) > 0) {
define	('tvseries', html_entity_decode(stripslashes_deep($fmovie_tvseries), ENT_QUOTES));
 } else {
define	('tvseries', 'TV Series');
}
if (strlen($fmovie_slogan) > 0) {
define	('slogan', html_entity_decode(stripslashes_deep($fmovie_slogan), ENT_QUOTES));
} else {
define	('slogan', ''.ucfirst($array["host"]).' is top of free streaming website, where to watch movies online free without registration required. With a big database and great features, we\'re confident.');
}
if (strlen($fmovie_play) > 0) {
define	('play', html_entity_decode(stripslashes_deep($fmovie_play), ENT_QUOTES));
 } else {
define	('play', 'Play');
}
if (strlen($fmovie_share) > 0) {
define	('share', html_entity_decode(stripslashes_deep($fmovie_share), ENT_QUOTES));
 } else {
define	('share', 'Report');
}
if (strlen($fmovie_trailer) > 0) {
define	('trailer', html_entity_decode(stripslashes_deep($fmovie_trailer), ENT_QUOTES));
 } else {
define	('trailer', 'Trailer');
}
if (strlen($fmovie_streaming) > 0) {
define	('streaming', html_entity_decode(stripslashes_deep($fmovie_streaming), ENT_QUOTES));
 } else {
define	('streaming', 'Toggle light');
}
if (strlen($fmovie_advertise) > 0) {
define	('advertise', html_entity_decode(stripslashes_deep($fmovie_advertise), ENT_QUOTES));
 } else {
define	('advertise', 'Advertise Here');
}
//if (strlen($fmovie_email) > 0) {
//define	('email', html_entity_decode(stripslashes_deep($fmovie_email), ENT_QUOTES));
 //} else {
//define	('email', html_entity_decode(stripslashes_deep($fmovie_email), ENT_QUOTES));
//}
if (strlen($fmovie_recently) > 0) {
define	('recently', html_entity_decode(stripslashes_deep($fmovie_recently), ENT_QUOTES));
 } else {
define	('recently', 'Sort by');
}
if (strlen($fmovie_mostrated) > 0) {
define	('mostrated', html_entity_decode(stripslashes_deep($fmovie_mostrated), ENT_QUOTES));
 } else {
define	('mostrated', 'Rating');
}
if (strlen($fmovie_mostwatched) > 0) {
define	('mostwatched', html_entity_decode(stripslashes_deep($fmovie_mostwatched), ENT_QUOTES));
 } else {
define	('mostwatched', 'Views');
}
if (strlen($fmovie_testolike) > 0) {
define	('testolike', html_entity_decode(stripslashes_deep($fmovie_testolike), ENT_QUOTES));
 } else {
define	('testolike', 'Like');
}
if (strlen($fmovie_releasedate) > 0) {
define	('releasedate', html_entity_decode(stripslashes_deep($fmovie_releasedate), ENT_QUOTES));
 } else {
define	('releasedate', 'Date');
}
if (strlen($fmovie_titleato) > 0) {
define	('titleato', html_entity_decode(stripslashes_deep($fmovie_titleato), ENT_QUOTES));
 } else {
define	('titleato', 'Title');
}
if (strlen($fmovie_fullbio) > 0) {
define	('fullbio', html_entity_decode(stripslashes_deep($fmovie_fullbio), ENT_QUOTES));
 } else {
define	('fullbio', 'Full Bio');
}
if (strlen($fmovie_nobio) > 0) {
define	('nobio', html_entity_decode(stripslashes_deep($fmovie_nobio), ENT_QUOTES));
 } else {
define	('nobio', 'No bio available for');
}
if (strlen($fmovie_textautoembed) > 0) {
define	('textautoembed', html_entity_decode(stripslashes_deep($fmovie_textautoembed), ENT_QUOTES));
 } else {
define	('textautoembed', 'If current server doesn\'t work please try other servers below.');
}
if (strlen($fmovie_textmultiserver) > 0) {
define	('textmultiserver', html_entity_decode(stripslashes_deep($fmovie_textmultiserver), ENT_QUOTES));
 } else {
define	('textmultiserver', 'Multi Server');
}
if (strlen($fmovie_textfavorites) > 0) {
define	('textfavorites', html_entity_decode(stripslashes_deep($fmovie_textfavorites), ENT_QUOTES));
 } else {
define	('textfavorites', 'Favorites');
}
if (strlen($fmovie_txtnoletter) > 0) {
define	('txtnoletter', html_entity_decode(stripslashes_deep($fmovie_txtnoletter), ENT_QUOTES));
 } else {
define	('txtnoletter', 'Sorry, but nothing matched this letter.');
}
if (strlen($fmovie_related) > 0) {
define	('related', html_entity_decode(stripslashes_deep($fmovie_related), ENT_QUOTES));
 } else {
define	('related', 'You may also like');
}
if (strlen($fmovie_monetize) > 0) {
define	('monetize', html_entity_decode(stripslashes_deep($fmovie_monetize), ENT_QUOTES));
 } else {
define	('monetize', '#');
}
if (strlen($fmovie_language) > 0) {
define	('language', html_entity_decode(stripslashes_deep($fmovie_language), ENT_QUOTES));
 } else {
define	('language', 'Language');
}
if (strlen($fmovie_txtquality) > 0) {
define	('txtquality', html_entity_decode(stripslashes_deep($fmovie_txtquality), ENT_QUOTES));
 } else {
define	('txtquality', 'Quality');
}
if (strlen($fmovie_txtcomments) > 0) {
define	('txtcomments', html_entity_decode(stripslashes_deep($fmovie_txtcomments), ENT_QUOTES));
 } else {
define	('txtcomments', 'Comments');
}
if (strlen($fmovie_deschome) > 0) {
define	('deschome', html_entity_decode(stripslashes_deep($fmovie_deschome), ENT_QUOTES));
} else {
define	('deschome', ''.ucfirst($array["host"]).' - Just a better place to <strong>watch movies online for free</strong>. It allows you to <strong>watch movies online</strong> in high quality for free. No registration is required. The content is updated daily with fast streaming servers, multi-language subtitles supported. Just open '.get_bloginfo( 'name' ).' and watch your favorite movies, tv-shows. We have almost any movie, tv-shows you want to watch!<br>
Please bookmark <label class="badge badge-secondary">'.$array["host"].'</label> to update about '.get_bloginfo( 'name' ).' domains.');
}
if (strlen($fmovie_sponsor1) > 0) {
define	('sponsor1', html_entity_decode(stripslashes_deep($fmovie_sponsor1), ENT_QUOTES));
} else {
define	('sponsor1', '#');
}
if (strlen($fmovie_sponsor2) > 0) {
define	('sponsor2', html_entity_decode(stripslashes_deep($fmovie_sponsor2), ENT_QUOTES));
} else {
define	('sponsor2', '#');
}
if (strlen($fmovie_button1) > 0) {
define	('button1', html_entity_decode(stripslashes_deep($fmovie_button1), ENT_QUOTES));
} else {
define	('button1', 'Stream in HD');
}
if (strlen($fmovie_button2) > 0) {
define	('button2', html_entity_decode(stripslashes_deep($fmovie_button2), ENT_QUOTES));
} else {
define	('button2', 'Download in HD');
}
if (strlen($fmovie_server_0_text) > 0) {
define	('premium_text', html_entity_decode(stripslashes_deep($fmovie_server_0_text), ENT_QUOTES));
 } else {
define	('premium_text', 'Premium');
}
if (strlen($fmovie_server_0_text) > 0) {
define	('server_0_text', html_entity_decode(stripslashes_deep($fmovie_server_0_text), ENT_QUOTES));
 } else {
define	('server_0_text', 'Premium');
}
if (strlen($fmovie_server_1_text) > 0) {
define	('server_1_text', html_entity_decode(stripslashes_deep($fmovie_server_1_text), ENT_QUOTES));
 } else {
define	('server_1_text', '2embed');
}
if (strlen($fmovie_server_2_text) > 0) {
define	('server_2_text', html_entity_decode(stripslashes_deep($fmovie_server_2_text), ENT_QUOTES));
 } else {
define	('server_2_text', 'Multi');
}
if (strlen($fmovie_server_3_text) > 0) {
define	('server_3_text', html_entity_decode(stripslashes_deep($fmovie_server_3_text), ENT_QUOTES));
 } else {
define	('server_3_text', 'Vidsrc');
}
if (strlen($fmovie_textlatest) > 0) {
define	('textlatest', html_entity_decode(stripslashes_deep($fmovie_textlatest), ENT_QUOTES));
 } else {
 define	('textlatest', 'Latest');
}
if (strlen($fmovie_textviewall) > 0) {
define	('textviewall', html_entity_decode(stripslashes_deep($fmovie_textviewall), ENT_QUOTES));
 } else {
 define	('textviewall', 'View all');
}
if (strlen($fmovie_btn_more) > 0) {
define	('btn_more', html_entity_decode(stripslashes_deep($fmovie_btn_more), ENT_QUOTES));
 } else {
define	('btn_more', 'more');
 }
if (strlen($fmovie_btn_less) > 0) {
define	('btn_less', html_entity_decode(stripslashes_deep($fmovie_btn_less), ENT_QUOTES));
 } else {
define	('btn_less', 'less');
 }
//admin end
