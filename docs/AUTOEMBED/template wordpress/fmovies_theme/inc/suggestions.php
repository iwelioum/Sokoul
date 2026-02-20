<?php
/**
 * Theme live suggestions with suggestions
 *
 * @package fmovie
 */

//get meta
function fmovie_get_meta( $value ) {
	global $post;
	$field = get_post_meta( $post->ID, $value, true );
	if ( ! empty( $field ) ) {
		return is_array( $field ) ? stripslashes_deep( $field ) : stripslashes( wp_kses_decode_entities( $field ) );
	} else {
		return false;
	}
}

//clear text
function fmovie_clear($text) {
	return wp_strip_all_tags(html_entity_decode($text));
}

//enqueue_script ajax_suggestions
function ajax_suggestions() {
	wp_enqueue_script('suggestions', get_template_directory_uri() .'/assets/js/min/suggestions.min.js', array('jquery'), '55', true );
	wp_localize_script( 
		'suggestions', 
		'Suggestions', 
		array( 
			'api' => fmovie_url_suggestions(),
			'nonce' => fmovie_create_nonce('fmovie-suggestions-nonce'),
			'area' => ".suggestions",
			'more' => "View all",
			'disqus_id' => disqus,
			) 
		);
}
add_action('wp_enqueue_scripts', 'ajax_suggestions');

//verify nonce
function fmovie_verify_nonce( $id, $value ) {
    $nonce = get_option( $id );
    if( $nonce == $value )
        return true;
    return false;
}

//create nonce
function fmovie_create_nonce( $id ) {
    if( ! get_option( $id ) ) {
        $nonce = wp_create_nonce( $id );
        update_option( $id, $nonce );
    }
    return get_option( $id );
}

//API url
function fmovie_url_suggestions() {
	return rest_url('/fmovie/suggestions/');
}

//rest_route
function wpc_register_wp_api_suggestions() {
	register_rest_route('fmovie', '/suggestions/', array(
        'methods' => 'GET',
        'callback' => 'fmovie_ajax_suggestions',
		'permission_callback' => '__return_true',
    ));
}
add_action('rest_api_init', 'wpc_register_wp_api_suggestions');

//suggestion ajax
function fmovie_ajax_suggestions( $request_data ) {
   	$parameters = $request_data->get_params();
    $keyword = fmovie_clear($parameters['keyword']);
    $nonce = fmovie_clear($parameters['nonce']);
	$types = array('post');
	if( !fmovie_verify_nonce('fmovie-suggestions-nonce', $nonce ) ) return array('error' => 'no_verify_nonce', 'title' => 'No data nonce' );
	if( !isset( $keyword ) || empty($keyword) ) return array('error' => 'no_parameter_given');
	if( strlen( $keyword ) <= 2 ) return array('error' => 'keyword_not_long_enough', 'title' => '' );

	$args = array(
		's' => $keyword,
		'post_type' => $types,
		'post_status' => 'publish',
		'posts_per_page' => 5
	);
    $query = new WP_Query( $args );
    if ( $query->have_posts() ) {
    	$data = array();
        while ( $query->have_posts() ) {
            $query->the_post();
            global $post;
            $data[$post->ID]['title'] = $post->post_title;
            $data[$post->ID]['url'] = get_the_permalink();
			$data[$post->ID]['poster']	= 'https://image.tmdb.org/t/p/w600_and_h900_bestv2'.esc_html(get_post_meta($post -> ID, 'poster_path', true));
			if($dato = fmovie_get_meta('release_date')) {
			$data[$post->ID]['extra']['release_date'] = substr($dato, 0, 4);
			}
			$vote_average = fmovie_get_meta('vote_average');
			$vote_average = substr($vote_average, 0, 3);
			$data[$post->ID]['extra']['vote_average'] = $vote_average;
        }
        return $data;
    } else {
    	return array('error' => 'no_posts', 'title' => 'No results' );
    }
    wp_reset_postdata();
}