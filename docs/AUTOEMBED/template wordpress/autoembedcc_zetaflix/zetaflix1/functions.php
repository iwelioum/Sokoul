<?php

/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
* @since 1.0.4
*/


# Theme options
define('ZETA_THEME_DOWNLOAD_MOD', true);
define('ZETA_THEME_PLAYER_MOD',   true);
define('ZETA_THEME_OMEGADB',     true);
define('ZETA_THEME_USER_MOD',     true);
define('ZETA_THEME_VIEWS_COUNT',  true);
define('ZETA_THEME_RELATED',      true);
define('ZETA_THEME_SOCIAL_SHARE', true);
define('ZETA_THEME_CACHE',        true);
define('ZETA_THEME_PLAYERSERNAM', true);
define('ZETA_THEME_JSCOMPRESS',   true);
define('ZETA_THEME_TOTAL_POSTC',  true);
define('ZETA_THEME_LAZYLOAD',     false);

# Repository data
define('ZETA_COM','Zetathemes');
define('ZETA_VERSION','1.0.5');
https://cdn.bescraper.cf/api
define('ZETA_VERSION_DB','1.0');
define('ZETA_ITEM_ID','234');
define('ZETA_PHP_REQUIRE','7.1');
define('ZETA_THEME','zetaflix');
define('ZETA_THEME_SLUG','zetaflix');
define('ZETA_THEME_TYPE', 'themes');
define('ZETA_THEME_LOG', 'updatelog');
define('ZETA_SERVER','https://cdn.bescraper.cf/api');
define('ZETA_GICO','https://s2.googleusercontent.com/s2/favicons?domain=');


# Configure Here date format #
define('ZETA_TIME','M. d, Y');  // More Info >>> https://www.php.net/manual/function.date.php
##############################



# Define Rating data
define('ZETA_MAIN_RATING','_starstruck_avg');
define('ZETA_MAIN_VOTOS','_starstruck_total');

# Define Options key

define('ZETA_OPTIONS','_zetaflix_options');
define('ZETA_CUSTOMIZE', '_zetaflix_customize');

# Define template directory
define('ZETA_URI',get_template_directory_uri());
define('ZETA_DIR',get_template_directory());


# Translations
load_theme_textdomain('zetaflix', ZETA_DIR.'/lang/');


# Load Application
require get_parent_theme_file_path('/inc/zeta_init.php');

$adminbar = zeta_is_true('permits', 'sab');

if(!$adminbar){
	remove_action('init', 'wp_admin_bar_init');
	add_filter( 'show_admin_bar', '__return_false' );
}


/* Custom functions
========================================================
*/

