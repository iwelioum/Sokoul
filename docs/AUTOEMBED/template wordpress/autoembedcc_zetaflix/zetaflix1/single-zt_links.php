<?php
/*
* -------------------------------------------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* -------------------------------------------------------------------------------------
*
* @since 1.0.0
*
*/

// Link Data
global $post;
$ourl = get_post_meta($post->ID, '_zetal_url', true);
$murl = ZetaLinks::shorteners($ourl);
$time = zeta_get_option('linktimewait');
// Get Post Link
if(have_posts()){
    while(have_posts()){
        // The Post
        the_post();
        // Count view
        zeta_set_views($post->ID);
        // Check wait time
        if(!$time){
            // Redirect to URL
            wp_redirect($murl, 301);
            // Exit to new URL
            exit;
        }else{
            // Compose Options
            $outp = zeta_get_option('linkoutputtype','btn');
            $btxt = zeta_get_option('linkbtntext', __z('Continue'));
            $txun = zeta_get_option('linkbtntextunder', __z('Click on the button to continue'));
            $clor = zeta_get_option('linkbtncolor','#1e73be');
            $ganl = zeta_get_option('ganalytics');
            // Compose Ad banners
            $adst = zeta_compose_ad('_zetaflix_adlinktop');
            $adsb = zeta_compose_ad('_zetaflix_adlinkbottom');
            // Get data of parent
            $prnt = wp_get_post_parent_id($post->ID);
            $titl = get_the_title($prnt);
            $prml = get_permalink($prnt);
            // Get post meta
            $type = get_post_meta($post->ID, '_zetal_type', true );
            $lang = get_post_meta($post->ID, '_zetal_lang', true );
            $size = get_post_meta($post->ID, '_zetal_size', true );
            $qual = get_post_meta($post->ID, '_zetal_quality', true );
            $domn = zeta_compose_domainname($ourl);
            // Compose Json string
            $json = array(
                'time' => $time,
                'exit' => $outp,
                'ganl' => $ganl
            );
            // The json
            $json = json_encode($json);
            // Load Template
            require_once( ZETA_DIR.'/inc/parts/single/zeta_links.php');
        }
    }
}
