<?php
/* ----------------------------------------------------
* Template Name: ZT - jwplayer
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
*
* @since 1.0.0
*
*/

// Libraries and dynamic data
$google = new ZetaGdrive;
$source = urldecode(zeta_isset($_GET,'source'));
$typeso = zeta_isset($_GET,'type');
$postid = zeta_isset($_GET,'id');
$images = get_post_meta($postid,'imagenes', true);
$jwpkey = zeta_get_option('jwkey','IMtAJf5X9E17C1gol8B45QJL5vWOCxYUDyznpA==');
$jw8key = zeta_get_option('jw8key','64HPbvSQorQcd52B8XFuhMtEoitbvY/EXJmMBfKcXZQU2Rnn');
$libray = zeta_get_option('player','plyr');
$mp4fle = ($typeso == 'gdrive') ? $google->GetUrl($source) : $source;
$prvimg = omegadb_get_rand_image($images);
$plyrcl = zeta_get_option('playercolor','#d40b12');

// Compose data for Json
$data = array(
    'file'  => $mp4fle,
    'image' => $prvimg,
    'color' => $plyrcl,
    'link'  => esc_url(home_url()),
    'logo'  => zeta_compose_image_option('jwlogo'),
    'auto'  => zeta_is_true('playauto','jwp') ? 'true' : 'false',
    'text'  => zeta_get_option('jwabout','ZetaPlay Theme WordPress'),
    'lposi' => zeta_get_option('jwposition','top-right'),
    'flash' =>  ZETA_URI.'/assets/jwplayer/jwplayer.flash.swf'
);

// Render JW Player
require_once(ZETA_DIR.'/pages/sections/'.$libray.'.php');
