<?php

add_action('generate_rewrite_rules', 'fmovie_fake_rw');
function fmovie_fake_rw($wp_rewrite) {
   $newrules = array();
   $new_rules['^fake.php$'] = 'index.php?fake=true';
   $wp_rewrite->rules = $new_rules + $wp_rewrite->rules;
}

add_action( 'query_vars', 'fmovie_fake_query_vars' );

function fmovie_fake_query_vars( $query_vars )
{
    $query_vars[] = 'fake';
    return $query_vars;
}

add_action( 'parse_request', 'fmovie_fake_parse_request' );
function fmovie_fake_parse_request( &$wp )
{
    if ( array_key_exists( 'fake', $wp->query_vars ) ) {
        require_once get_template_directory() . '/fake/fake.php';
        exit();
    }
}