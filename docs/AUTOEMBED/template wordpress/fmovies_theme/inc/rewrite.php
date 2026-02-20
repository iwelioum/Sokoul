<?php
/**
 * Theme rewrite rules
 *
 * @package fmovie
 */

// player tv

$fmovie_rewrite = get_option('admin_rewrite');
if ($fmovie_rewrite == 1) { 
//null
} else {

add_action('generate_rewrite_rules', 'fmovie_tv_rw');
function fmovie_tv_rw($wp_rewrite) {
   $newrules = array();
   $new_rules['^player-tv.php$'] = 'index.php?tv=true';
   $wp_rewrite->rules = $new_rules + $wp_rewrite->rules;
}

add_action( 'query_vars', 'fmovie_tv_query_vars' );
function fmovie_tv_query_vars( $query_vars )
{
    $query_vars[] = 'tv';
    return $query_vars;
}

add_action( 'parse_request', 'fmovie_tv_parse_request' );
function fmovie_tv_parse_request( &$wp )
{
    if ( array_key_exists( 'tv', $wp->query_vars ) ) {
        require_once get_template_directory() . '/player/player-tv.php';
        exit();
    }
}

// get play tv
add_action('generate_rewrite_rules', 'fmovie_getplay_tv_rw');
function fmovie_getplay_tv_rw($wp_rewrite) {
   $newrules = array();
   $new_rules['^getPlayTV.php$'] = 'index.php?playtv=true';
   $wp_rewrite->rules = $new_rules + $wp_rewrite->rules;
}

add_action( 'query_vars', 'fmovie_getplay_tv_query_vars' );
function fmovie_getplay_tv_query_vars( $query_vars )
{
    $query_vars[] = 'playtv';
    return $query_vars;
}

add_action( 'parse_request', 'fmovie_getplay_tv_parse_request' );
function fmovie_getplay_tv_parse_request( &$wp )
{
    if ( array_key_exists( 'playtv', $wp->query_vars ) ) {
        require_once get_template_directory() . '/player/getPlayTV.php';
        exit();
    }
}
}
