<?php
/**
 * Theme favorites
 *
 * @package fmovie
 */

//favorites
function enqueue_favorite(){

    if( !is_page('favorites') ){ // prevent this script from loading if user is on favorite movies page 
	  wp_enqueue_script( 'favorites', get_template_directory_uri() . '/assets/js/favorites.js', array(), wp_get_theme()->get( 'Version' ), true);
    } 
} 
add_action('wp_enqueue_scripts' ,'enqueue_favorite');


function favorites_scripts(){

    if( is_page('favorites') ){ 
		wp_enqueue_script( 'ajax-favorites', get_template_directory_uri() . '/assets/js/ajax.favorites.js', array(), wp_get_theme()->get( 'Version' ), true);
        wp_localize_script('ajax-favorites', 'Favorites', array('ajax_url' => admin_url('admin-ajax.php') ) );
    }
}
add_action('wp_enqueue_scripts', 'favorites_scripts');

function display_fav_movies(){ 
    
	$favorite_movies_list = $_POST['favorite_movies_list'] ?? null;
	
    if( !empty($favorite_movies_list) ){

        $loop = new WP_Query( array( 
            'post_type' => 'post',
			'post_status' => 'publish',
            'nopaging' => true,
            'ignore_sticky_posts' => true,
            'post__in' => $favorite_movies_list,
			'no_found_rows' => true
        )); 
        
		$fav_movies_page = 1;

		if ($loop->have_posts()) {
			while ($loop->have_posts()) {
				$loop->the_post();
				get_template_part('template-parts/content/content', 'loop');
			}
		}
	} else {
		echo false;
	}
	wp_die();
}
add_action('wp_ajax_display_fav_movies', 'display_fav_movies');
add_action('wp_ajax_nopriv_display_fav_movies', 'display_fav_movies');