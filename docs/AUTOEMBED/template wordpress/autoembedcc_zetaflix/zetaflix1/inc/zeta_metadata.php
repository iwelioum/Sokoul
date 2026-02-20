<?php
/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
*
* @since 1.0.0
*
*/



/**
 * @since 1.0.0
 * @version 1.0
 */
function zeta_meta_isset($data, $meta){
    return (isset($data[$meta][0])) ? $data[$meta][0] : false;
}


/**
 * @since 1.0.0
 * @version 1.1
 */
function zeta_postmeta_movies($post_id){
    // All post meta
    $cache = new ZetaFlixCache;
    $pdata = $cache->get($post_id.'_postmeta');
	
	//Check post type

	
    // Verify cache
    if(!$pdata){
        // In database
        $post_meta = get_post_meta($post_id);


        // compose data
        $pdata = array(
			'zt_season_poster' => zeta_meta_isset($post_meta, 'zt_season_poster'),
            'zt_featured_post' => zeta_meta_isset($post_meta,'zt_featured_post'),
			'zt_featured_slider' => zeta_meta_isset($post_meta,'zt_featured_slider'),
			'zt_logo'          => zeta_meta_isset($post_meta,'zt_logo'),
            'zt_poster'        => zeta_meta_isset($post_meta,'zt_poster'),
            'zt_backdrop'      => zeta_meta_isset($post_meta,'zt_backdrop'),
            'imagenes'         => zeta_meta_isset($post_meta,'imagenes'),
            'youtube_id'       => zeta_meta_isset($post_meta,'youtube_id'),
            'imdbRating'       => zeta_meta_isset($post_meta,'imdbRating'),
            'imdbVotes'        => zeta_meta_isset($post_meta,'imdbVotes'),
            'Rated'            => zeta_meta_isset($post_meta,'Rated'),
            'Country'          => zeta_meta_isset($post_meta,'Country'),
            'idtmdb'           => zeta_meta_isset($post_meta,'idtmdb'),
            'original_title'   => zeta_meta_isset($post_meta,'original_title'),
            'tagline'          => zeta_meta_isset($post_meta,'tagline'),
            'release_date'     => zeta_meta_isset($post_meta,'release_date'),
            'vote_average'     => zeta_meta_isset($post_meta,'vote_average'),
            'vote_count'       => zeta_meta_isset($post_meta,'vote_count'),
            'runtime'          => zeta_meta_isset($post_meta,'runtime'),
            'zt_cast'          => zeta_meta_isset($post_meta,'zt_cast'),
            'zt_dir'           => zeta_meta_isset($post_meta,'zt_dir'),
            'zt_string'        => zeta_meta_isset($post_meta,'zt_string'),
            'urating_avg'      => zeta_meta_isset($post_meta,'_starstruck_avg'),
            'urating_total'    => zeta_meta_isset($post_meta,'_starstruck_total'),
            'numreport'        => zeta_meta_isset($post_meta,'numreport'),
            'zt_views_count'   => zeta_meta_isset($post_meta,'zt_views_count'),
            'players'          => zeta_meta_isset($post_meta,'repeatable_fields')
        );
        // Update cache
        $cache->set($post_id.'_postmeta', serialize($pdata));
    }else{
        $pdata = maybe_unserialize($pdata);
    }
    // The return
    return apply_filters('zeta_postmeta_movies', $pdata, $post_id);
}



/**
 * @since 1.0.0
 * @version 1.0
 */
function zeta_postmeta_tvshows($post_id){
    // All post meta
    $cache = new ZetaFlixCache;
    $pdata = $cache->get($post_id.'_postmeta');
    // Verify cache
    if(!$pdata){
        // In database
        $post_meta = get_post_meta($post_id);
        // compose data
        $pdata = array(
            'zt_featured_post'   => zeta_meta_isset($post_meta,'zt_featured_post'),
			'zt_featured_slider'   => zeta_meta_isset($post_meta,'zt_featured_slider'),
            'clgnrt'             => zeta_meta_isset($post_meta,'clgnrt'),
            'ids'                => zeta_meta_isset($post_meta,'ids'),
			'zt_logo'          => zeta_meta_isset($post_meta,'zt_logo'),
            'zt_poster'          => zeta_meta_isset($post_meta,'zt_poster'),
            'zt_backdrop'        => zeta_meta_isset($post_meta,'zt_backdrop'),
            'imagenes'           => zeta_meta_isset($post_meta,'imagenes'),
            'youtube_id'         => zeta_meta_isset($post_meta,'youtube_id'),
            'original_name'      => zeta_meta_isset($post_meta,'original_name'),
            'first_air_date'     => zeta_meta_isset($post_meta,'first_air_date'),
            'last_air_date'      => zeta_meta_isset($post_meta,'last_air_date'),
            'imdbRating'         => zeta_meta_isset($post_meta,'imdbRating'),
            'imdbVotes'          => zeta_meta_isset($post_meta,'imdbVotes'),
            'number_of_seasons'  => zeta_meta_isset($post_meta,'number_of_seasons'),
            'number_of_episodes' => zeta_meta_isset($post_meta,'number_of_episodes'),
            'zt_cast'            => zeta_meta_isset($post_meta,'zt_cast'),
            'zt_creator'         => zeta_meta_isset($post_meta,'zt_creator'),
            'urating_avg'        => zeta_meta_isset($post_meta,'_starstruck_avg'),
            'urating_total'      => zeta_meta_isset($post_meta,'_starstruck_total'),
            'episode_run_time'   => zeta_meta_isset($post_meta,'episode_run_time'),
            'zt_views_count'     => zeta_meta_isset($post_meta,'zt_views_count')
        );
        // Update cache
        $cache->set($post_id.'_postmeta', serialize($pdata));
    }else{
        $pdata = maybe_unserialize($pdata);
    }
    // The return
    return apply_filters('zeta_postmeta_tvshows', $pdata, $post_id);
}


/**
 * @since 1.0.0
 * @version 1.0
 */
function zeta_postmeta_seasons($post_id){
    // All post meta
    $cache = new ZetaFlixCache;
    $pdata = $cache->get($post_id.'_postmeta');
    // Verify cache
    if(!$pdata){
        // In database
        $post_meta = get_post_meta($post_id);
        // compose data
        $pdata = array(
			'tvshowid'            => zeta_meta_isset($post_meta,'tvshowid'),
            'ids'            => zeta_meta_isset($post_meta,'ids'),
            'temporada'      => zeta_meta_isset($post_meta,'temporada'),
            'clgnrt'         => zeta_meta_isset($post_meta,'clgnrt'),
            'serie'          => zeta_meta_isset($post_meta,'serie'),
            'air_date'       => zeta_meta_isset($post_meta,'air_date'),
            'zt_views_count' => zeta_meta_isset($post_meta,'zt_views_count')
        );
        // Update cache
        $cache->set($post_id.'_postmeta', serialize($pdata));
    }else{
        $pdata = maybe_unserialize($pdata);
    }
    // The return
    return apply_filters('zeta_postmeta_seasons', $pdata, $post_id);
}



/**
 * @since 1.0.0
 * @version 1.1
 */
function zeta_postmeta_episodes($post_id){
    // All post meta
    $cache = new ZetaFlixCache;
    $pdata = $cache->get($post_id.'_postmeta');
    // Verify cache
    if(!$pdata){
        // In database
        $post_meta = get_post_meta($post_id);
		
        // compose data
        $pdata = array(
			'tvshowid'		   => zeta_meta_isset($post_meta,'tvshowid'),
			'seasonid'		   => zeta_meta_isset($post_meta,'seasonid'),
            'ids'            => zeta_meta_isset($post_meta,'ids'),
            'temporada'      => zeta_meta_isset($post_meta,'temporada'),
            'episodio'       => zeta_meta_isset($post_meta,'episodio'),
            'episode_name'   => zeta_meta_isset($post_meta,'episode_name'),
            'serie'          => zeta_meta_isset($post_meta,'serie'),
            'zt_backdrop'    => zeta_meta_isset($post_meta,'zt_backdrop'),
            'imagenes'       => zeta_meta_isset($post_meta,'imagenes'),
            'air_date'       => zeta_meta_isset($post_meta,'air_date'),
            'zt_string'      => zeta_meta_isset($post_meta,'zt_string'),
            'zt_views_count' => zeta_meta_isset($post_meta,'zt_views_count'),
            'players'        => zeta_meta_isset($post_meta,'repeatable_fields')
        );
        // Update cache
        $cache->set($post_id.'_postmeta', serialize($pdata));
    }else{
        $pdata = maybe_unserialize($pdata);
    }
    // The return
    return apply_filters('zeta_postmeta_seasons', $pdata, $post_id);
}
