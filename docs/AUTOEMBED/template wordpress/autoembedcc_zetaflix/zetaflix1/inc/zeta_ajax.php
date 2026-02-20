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
/* Top STream
========================================================
*/
if(!function_exists('zt_top_stream_widget')) {
	function zt_top_stream_widget() {
		set_time_limit(30);
		$nonce = zeta_isset($_POST,'security');
		$count = zeta_isset($_POST,'num');
		$count = ($count > 10) ? 10 : $count;
		$type = zeta_isset($_POST,'tab');
		$type = ($type == 'tvshows') ? 'tvshows' : 'movies';
		
		$status = 0;
		$results = '';
		if( isset($nonce ) and wp_verify_nonce($nonce, 'zt-top-stream-widget') ) {
			$num = 1;
			$transient = get_transient('zetaflix_popular_widget_'.$type);
			if(false === $transient){
				$transient = new WP_Query( array('post_type' => $type, 'showposts' => $count) );
				set_transient('zetaflix_popular_widget_'.$type, $transient, MINUTE_IN_SECONDS*60);
			}
			
			if($transient->have_posts()){
				global $post;
				$items = [];
				while ( $transient->have_posts() ) : $transient->the_post(); 
			

				$postmeta = ($type == 'tvshows') ? zeta_postmeta_tvshows(get_the_id()) : zeta_postmeta_movies(get_the_id());
			
				$quality = get_the_term_list(get_the_id(), 'ztquality');
				$urating = zeta_isset($postmeta, '_starstruck_avg');
				$imdbrat = zeta_isset($postmeta, 'imdbRating');
				$imdbrat = (!empty($imdbrat)) ? $imdbrat : __('--');

				$imdbtit = ($type == 'tvshows') ?  __z('TMDb') :  __z('IMDb');
				$release = ($type == 'tvshows') ? zeta_isset($postmeta, 'first_air_date') : zeta_isset($postmeta, 'release_date');
				$ptitl = get_the_title();
				$plink = esc_url(get_permalink());
				
				$year = substr($release, 0, 4);
				$year = ($year) ? $year : 'n/a';
				
				$runtime = ($type == 'tvshows') ? zeta_isset($postmeta, 'episode_run_time') : zeta_isset($postmeta, 'runtime');
				$runtime = ($runtime) ? $runtime.' '.__z('min') : 'n/a';
				$watch = ($type == 'tvshows') ? __z('Watch TV Show') : __z('Watch Movie');
				$thumb = omegadb_get_poster('', get_the_id());
				
				// Sanitize
				$num = esc_html($num);
				$imdbrat =  esc_html($imdbrat);
				$ptitl	= esc_html($ptitl);
				$plink = esc_url($plink);
				$year	= esc_html($year);
				$runtime	= esc_html($runtime);
				$thumb = esc_url($thumb);
				$imdbrat = esc_html($imdbrat);
				
				
				$results .= '<li><div class="top-item">';
				$results .= '<div class="top-item-left">';
				$results .= '<span class="top-rank">'.$num.'</span>';
				$results .= '<div class="top-poster"><img class="top-poster-img" src="'.$thumb.'"></div>';
				$results .= '</div>';
				$results .= '<div class="top-item-right">';
				$results .= '<div class="top-row"><span class="top-name">'.$ptitl.'</span></div>';
				$results .= '<div class="top-row"><span class="top-year">'.$year.'</span><span class="top-sep"></span><span class="top-runtime">'.$runtime.'</span></div>';
				$results .= '<div class="top-row">';
				$results .= '<div class="top-rating"><span class="top-rating-average"><i class="fa-solid fa-star"></i>'.$imdbtit.': '.$imdbrat.'</span></div>';
				$results .= '</div>';
				$results .= '<div class="top-row"><div class="top-view"><a href="'.$plink.'"><span class="top-watch-btn">'.$watch.'</span></a></div></div>';
				$results .= '</div>';
				$results .= '</div>';
				$results .= '</li>'; 
				
				
				
				
				
				$items[] = array('num' => $num, 'title' => $ptitl, 'url' => $plink, 'img' => $thumb,'quality' => $quality, 'star' => $urating, 'imdbt' => $imdbtit, 'imdbr' => $imdbrat, 'release' => $release, 'year' => $year, 'runtime' => $runtime, 'btn' => $watch );
				
				$num++; 

				endwhile;
						
				$status = 1;	
			}
			wp_reset_postdata();
		}
		
		echo json_encode(array('status' => 1, 'results' => $items, 'img' => 'test'));
		
		die();
	}
	add_action('wp_ajax_zt_top_stream', 'zt_top_stream_widget');
	add_action('wp_ajax_nopriv_zt_top_stream', 'zt_top_stream_widget');
}




/* Update user account page
========================================================
*/
if(!function_exists('zt_update_user_page')) {
	function zt_update_user_page() {
		set_time_limit(30);
		global $current_user, $wp_roles;

		$nonce = zeta_isset($_POST,'update-user-nonce');
		$pass1 = zeta_isset($_POST,'pass1');
		$pass2 = zeta_isset($_POST,'pass2');
		$fname = zeta_isset($_POST,'first-name');
		$lname = zeta_isset($_POST,'last-name');
		$dname = zeta_isset($_POST,'display_name');
		$twitt = zeta_isset($_POST,'twitter');
		$faceb = zeta_isset($_POST,'facebook');

		if( isset($nonce ) and wp_verify_nonce($nonce, 'update-user') ) {
			$error = array();

			wp_get_current_user();

			// update password
			if (!empty($pass1) && !empty($pass2)) {
				if ($pass1 == $pass2) {
					wp_update_user( array('ID' => $current_user->ID, 'user_pass' => esc_attr($pass1) ) );
				} else {
					echo '<div class="error"><i class="icon-times-circle"></i> '. __z('The passwords you entered do not match.  Your password was not updated.'). '</div>';
					exit;
				}
			}

			if(!empty($fname)) update_user_meta($current_user->ID, 'first_name', esc_attr($fname));
			if(!empty($lname)) update_user_meta($current_user->ID, 'last_name', esc_attr($lname));
			if(!empty($dname)) wp_update_user(array('ID' => $current_user->ID,'display_name' => esc_attr($dname)));

			update_user_meta($current_user->ID,'display_name', esc_attr($dname));
			update_user_meta($current_user->ID,'zt_twitter', esc_attr($twitt));
			update_user_meta($current_user->ID,'zt_facebook', esc_attr($faceb));
	

			if (count($error) == 0) {
				do_action('edit_user_profile_update', $current_user->ID);
				echo '<div class="success"><i class="icon-check-circle"></i> '. __z('Your profile has been updated.'). '</div>';
				exit;
			}
		}

		die();
	}
	add_action('wp_ajax_zt_update_user', 'zt_update_user_page');
	add_action('wp_ajax_nopriv_zt_update_user', 'zt_update_user_page');
}

/* Update user avatar
========================================================
*/
if(!function_exists('zt_update_user_icon_page')) {
	function zt_update_user_icon_page() {
		set_time_limit(30);
		global $current_user, $wp_roles;

		$nonce = zeta_isset($_POST,'update-user-icon-nonce');
		$icon = zeta_isset($_POST,'profile-icon');
		$current = get_user_meta($current_user->ID, 'zt_user_icon', true);

		if( isset($nonce ) and wp_verify_nonce($nonce, 'update-user-icon') ) {
			$error = array();

			wp_get_current_user();
			
			$img = '';

			// update password
			if ($icon) {
				if ($icon != $current ) {
					update_user_meta($current_user->ID, 'zt_user_icon', esc_attr($icon));
					$img = wp_get_attachment_image_src( $icon, 'full' );
					$img = $img[0];
				}
			} else {
				$msg = '<div class="error"><i class="icon-times-circle"></i> '. __z('No icon was selected, reload the page and try again.'). '</div>';
				echo json_encode(array('status' => 0, 'msg' => $msg));
				exit;
			}


			if (count($error) == 0) {
				do_action('edit_user_profile_update', $current_user->ID);
				$msg = '<div class="success"><i class="icon-check-circle"></i> '. __z('Your profile has been updated.'). '</div>';
				echo json_encode(array('status' => 1, 'msg' => $msg, 'img' => $img));
				exit;
			}
		}

		die();
	}
	add_action('wp_ajax_zt_update_user_icon', 'zt_update_user_icon_page');
	add_action('wp_ajax_nopriv_zt_update_user_icon', 'zt_update_user_icon_page');
}

/* Page list account / Movies and TVShows
========================================================
*/
if(!function_exists( 'next_page_list')){
	function next_page_list() {

		$paged    = zeta_isset($_POST,'page')+1;
		$type     = zeta_isset($_POST,'typepost');
		$user     = zeta_isset($_POST,'user');
		$template = zeta_isset($_POST,'template');
		$items = zeta_isset($_POST, 'items');
		$items = (!empty($items)) ? (int)$items : 18;

		$args = array(
		  'paged'			=> $paged,
		  'numberposts'		=> -1,
		  'orderby'			=> 'date',
		  'order'			=> 'DESC',
		  'post_type'		=> array('movies','tvshows','seasons'),
		  'posts_per_page'	=> $items,
		  'meta_query'		=> array (
				array (
				  'key' => $type,
				  'value' => 'u'.$user. 'r',
				  'compare' => 'LIKE'
				)
			)
		);

		$sep = '';
		$list_query = new WP_Query( $args );
		if ( $list_query->have_posts() ) : while ( $list_query->have_posts() ) : $list_query->the_post();
			 get_template_part('inc/parts/item_profile_'. $template);
		endwhile;
		else :
		echo '<div id="nocontent-msg" class="no_fav">'. __z('No more content to show.'). '</div>';
		endif; wp_reset_postdata();
		die();
	}
	add_action('wp_ajax_next_page_list', 'next_page_list');
	add_action('wp_ajax_nopriv_next_page_list', 'next_page_list');
}


/* Page list links
========================================================
*/
if(!function_exists('next_page_link')){
	function next_page_link() {
		$paged = zeta_isset($_POST,'page')+1;
		$user  = zeta_isset($_POST,'user');
		$args  = array(
		  'paged'          => $paged,
		  'orderby'        => 'date',
		  'order'          => 'DESC',
		  'post_type'      => 'zt_links',
		  'posts_per_page' => 10,
		  'post_status'    => array('pending', 'publish', 'trash'),
		  'author'         => $user,
		  );
		$list_query = new WP_Query( $args );
		if ( $list_query->have_posts() ) : while ( $list_query->have_posts() ) : $list_query->the_post();
			 get_template_part('inc/parts/item_links');
		endwhile;
		else :
		echo '<tr id="nocontent-msg"><td colspan="8">'.__z('No content').'</td></tr>';
		endif; wp_reset_postdata();
		die();
	}
	add_action('wp_ajax_next_page_link', 'next_page_link');
	add_action('wp_ajax_nopriv_next_page_link', 'next_page_link');
}

/* Page list links profile
========================================================
*/
if(!function_exists('next_page_link_profile')){
	function next_page_link_profile() {
		$paged = zeta_isset($_POST,'page')+1;
		$user  = zeta_isset($_POST,'user');
		$args  = array(
		  'paged'          => $paged,
		  'orderby'        => 'date',
		  'order'          => 'DESC',
		  'post_type'      => 'zt_links',
		  'posts_per_page' => 10,
		  'post_status'    => array('pending', 'publish', 'trash'),
		  'author'         => $user,
		  );
		$list_query = new WP_Query( $args );
		if ( $list_query->have_posts() ) : while ( $list_query->have_posts() ) : $list_query->the_post();
			 get_template_part('inc/parts/item_links_profile');
		endwhile;
		else :
		echo '<tr id="nocontent-msg"><td colspan="7">'.__z('No content').'</td></tr>';
		endif; wp_reset_postdata();
		die();
	}
	add_action('wp_ajax_next_page_link_profile', 'next_page_link_profile');
	add_action('wp_ajax_nopriv_next_page_link_profile', 'next_page_link_profile');
}

/* Page list Admin links
========================================================
*/
if(!function_exists('next_page_link_admin')){
	function next_page_link_admin() {
		$paged = zeta_isset($_POST,'page')+1;
		$args  = array(
		  'paged'          => $paged,
		  'orderby'        => 'date',
		  'order'          => 'DESC',
		  'post_type'      => 'zt_links',
		  'posts_per_page' => 10,
		  'post_status'    => array('pending'),
		  );
		$list_query = new WP_Query( $args );
		if ( $list_query->have_posts() ) : while ( $list_query->have_posts() ) : $list_query->the_post();
			 get_template_part('inc/parts/item_links_admin');
		endwhile;
		else :
		echo '<tr id="nocontent-msg"><td colspan="6">'.__z('No content').'</td></tr>';
		endif; wp_reset_postdata();
		die();
	}
	add_action('wp_ajax_next_page_link_admin', 'next_page_link_admin');
	add_action('wp_ajax_nopriv_next_page_link_admin', 'next_page_link_admin');
}

/* Control post link
========================================================
*/
if(!function_exists('control_link_user')){
	function control_link_user() {

		$post_id = zeta_isset($_POST,'post_id');
		$user_id = zeta_isset($_POST,'user_id');
		$status	 = zeta_isset($_POST,'status');

		$auhor_id = get_current_user_id();
		$stat = 0;
		$check = 0;
		$stts = '';
		$sttsnxt = '';
		if($auhor_id) {
		
		$args = array('ID' => $post_id,'post_status'=> $status);
			$post = wp_update_post( $args );
			//$post = $post_id;
			if($post){
				if($status == 'publish'){
					$check = 1;					
					$msg = "<div class='success'>".__z('Link enabled')."</div>";
					$txt = 'Disable';
					$stts = 'publish';
					$sttsnxt = 'pending';
				}elseif($status == 'pending'){
					$check = 2;
					$msg = "<div class='alert'>".__z('Link disabled')."</div>";
					$txt = 'Enable';
					$stts = 'pending'; 
					$sttsnxt = 'publish';
				}elseif($status == 'trash'){
					$msg = "<div class='error'>".__z('Link moved to trash')."</div>";
					$check = 3;
					$txt = 'Enable';
					$stts = 'trash'; 
					$sttsnxt = 'publish';
				}
			}
		}
		echo json_encode(array( 'check' => $check, 'msg' => $msg, 'pid' => $post, 'status' => $stts, 'btn' => $txt, 'data' => $sttsnxt ));    	
		die();
	}
	add_action('wp_ajax_control_link_user', 'control_link_user');
	add_action('wp_ajax_nopriv_control_link_user', 'control_link_user');
}


/* Live Search
========================================================
*/
if(!function_exists('zetaflix_live_search')){
	function zetaflix_live_search( $request_data ) {
	   	$parameters = $request_data->get_params();
	    $keyword    = isset($parameters['keyword']) ? zeta_clear_text($parameters['keyword']) : false;
	    $nonce      = isset($parameters['nonce']) ? zeta_clear_text($parameters['nonce']) : false;
		$types      = array('movies','tvshows');
		if(!zetaflix_verify_nonce('zetaflix-search-nonce', $nonce)) return array('error' => 'no_verify_nonce', 'title' => __z('No data nonce') );
		if(!isset( $keyword ) || empty($keyword)) return array('error' => 'no_parameter_given');
		if(strlen( $keyword ) <= 2 ) return array('error' => 'keyword_not_long_enough', 'title' => false);
		$args = array(
			's'              => $keyword,
			'post_type'      => $types,
			'posts_per_page' => 6
		);
	    $query = new WP_Query( $args );
	    if ( $query->have_posts() ) {
	    	$data = array();
	        while ( $query->have_posts() ) {
	            $query->the_post();
	            global $post;
				$defaultimg = ZETA_URI.'/assets/img/no/zt_backdrop.png';
				if(zeta_get_option('poster_style') == 'vertical'){
					$poster = omegadb_get_poster('', $post->ID, '92');					
					if($poster == $defaultimg){
						$poster = omegadb_get_backdrop('movies', $post->ID, 'w300', 'zt_backdrop', 'original');
					}else{
						$poster = $poster;
					}
				}else{
					$poster = omegadb_get_backdrop('movies', $post->ID, 'w300', 'zt_backdrop', 'original');
					if($poster == $defaultimg){
						$poster = omegadb_get_poster('', $post->ID, '');
					}else{
						$poster = $poster;
					}
				}
					
	            $data[$post->ID]['title'] = $post->post_title;
	            $data[$post->ID]['url'] = get_the_permalink();
                $data[$post->ID]['img'] = $poster;
				$data[$post->ID]['extra']['genres'] = 'n/a';
				$data[$post->ID]['extra']['date'] = 'n/a';
				$data[$post->ID]['extra']['imdb'] = 'n/a';
				if($genres = get_the_term_list($post->ID, 'genres', '', ', ', '')){
					$data[$post->ID]['extra']['genres'] = strip_tags($genres);
				}
				if($dato = zeta_get_postmeta('release_date')) {
					$data[$post->ID]['extra']['date'] = substr($dato, 0, 4);
				}
				if($dato = zeta_get_postmeta('first_air_date')) {
					$data[$post->ID]['extra']['date'] = substr($dato, 0, 4);
				}
				$data[$post->ID]['extra']['imdb'] = zeta_get_postmeta('imdbRating');
	        }
	        return $data;
	    } else {
	    	return array('error' => 'no_posts', 'title' => __z('No results') );
	    }
	    wp_reset_postdata();
	}
}

/* Live Glossary
========================================================
*/
if(!function_exists('zetaflix_live_glossary')){
	function zetaflix_live_glossary( $request_data ) {
	    $parameters = $request_data->get_params();
	    $term	    = isset($parameters['term']) ? zeta_clear_text($parameters['term']) : false;
		$nonce	    = isset($parameters['nonce']) ? zeta_clear_text($parameters['nonce']) : false;
	    $type       = isset($parameters['type']) ? zeta_clear_text($parameters['type']) : false;
		if( !zetaflix_verify_nonce('zetaflix-search-nonce', $nonce ) ) return array('error' => 'no_verify_nonce', 'title' => __z('No data nonce') );
	    if( !isset( $term ) || empty($term) ) return array('error' => 'no_parameter_given');
	    if( $type == 'all' )  $post_types = array('movies','tvshows'); else $post_types = $type;
	    $args = array(
	        'zeta_first_letter' => $term,
	        'post_type'        => $post_types,
			'post_status'      => 'publish',
	        'posts_per_page'   => 18,
	    	'orderby'          => 'rand',
	    );
	    query_posts( $args );
	    if(have_posts()){
	        $data = array();
	        while ( have_posts() ) {
	            the_post();
	            global $post;
	            $data[$post->ID]['title'] = $post->post_title;
	            $data[$post->ID]['url']   = get_the_permalink();
	            $data[$post->ID]['img']   = omegadb_get_poster($post->ID,'zt_poster_a','zt_poster','w185');
	            if($dato = zeta_get_postmeta('release_date')) $data[$post->ID]['year'] = substr($dato, 0, 4);

				if($dato = zeta_get_postmeta('first_air_date')) $data[$post->ID]['year'] = substr($dato, 0, 4);

				$data[$post->ID]['imdb'] = zeta_get_postmeta('imdbRating');
	        }
	        return $data;

	    } else {
	        return array('error' => 'no_posts', 'title' => __z('No results') );
	    }
	    wp_reset_query();
	}
}
/* Add TV Episode Players Viewer
========================================================
*/
if(!function_exists('zt_tv_episode')){
	function zt_tv_episode(){
		$postid	 = zeta_isset($_POST,'episode');
		$nonce	 = zeta_isset($_POST,'nonce');
		//$newdate = date("Y-m-d H:i:s");
		$status = 0;
		$msg = '<div class="error-tv"><span class="error-msg">'.__z('No video available.').'</span></div>';
		$vid = null;
		if($postid AND wp_verify_nonce( $nonce,'zt-tv-episode')) {
			$ajax_player = zeta_get_option('playajax');
			$postmeta = zeta_postmeta_episodes($postid);
			$trailer = zeta_isset($postmeta,'youtube_id');
			$player  = zeta_isset($postmeta,'players');
			$player  = maybe_unserialize($player);
			$set_mode    = ($ajax_player == true) ? 'ajax_mode' : 'no_ajax';
			
			if($player){
				$numv = 1;
				$numb = 1;
				if(!empty($player) && is_array($player)){
					
					$vhtml = '';
	                foreach($player as $play){
						// Set Source
						$source = zeta_isset($play,'url');
						$type = zeta_isset($play,'select');
						// HTML Player
						$vhtml .= "<div id='source-player-{$numv}' class='source-box'>";
						
						if(zeta_isset($play,'select') == 'mp4') {
								$vhtml .="<div class='pframe'><if"."rame class='metaframe rptss' src='{$play_pager}?source=".urlencode($source)."&id={$post}&type=mp4' frameborder='0' scrolling='no' allow='autoplay; encrypted-media' allowfullscreen></ifr"."ame></div>";
						}elseif(zeta_isset($play,'select') == 'iframe') {
								$vhtml .="<div class='pframe'><if"."rame class='metaframe rptss' src='{$source}' frameborder='0' scrolling='no' allow='autoplay; encrypted-media' allowfullscreen></ifr"."ame></div>";
						}elseif(zeta_isset($play,'select') == 'ztshcode') {
								$vhtml .= "<div class='pframe'>".do_shortcode($source)."</div>";
						}
						$vhtml .= "</div>";
						$numv++;
						
					}
					
			
				
				$html = "<div id='playeroptions' class='options player-options'><ul id='playeroptionsul' class='{$set_mode} play-lists'>";
				
					foreach($player as $play){
					$html .="<li id='player-option-{$numb}' class='zetaflix_player_option' data-type='tv' data-post='{$postid}' data-nume='{$numb}'><a>";
					$html .="<span class='play-list-ico'>";
					$html .="<i class='fas fa-play mr-2'></i>";
					$html .="</span>";
					$html .= "<span class='play-list-opt'>";
					$html .= "<span class='opt-titl'>".__z('Server')."</span>";
					$html .= "<span class='opt-name'>{$play['name']}</span>";
					$html .= "</span>";
					$html .="<span class='loader'></span></a></li>";
					$numb++;
					}					
				
				$html .="</ul></div>";			
				}				
				$msg = $html;	
				$vid = $vhtml;
				$status = 1;
			}
			
			
		}
		echo json_encode(array('status' => $status, 'msg' => $msg, 'vid' => $vid));
		die();
	}
	add_action('wp_ajax_zt_tv_episode', 'zt_tv_episode');
	add_action('wp_ajax_nopriv_zt_tv_episode', 'zt_tv_episode');
}
/* Add TV Episode Players Viewer
========================================================
*/
if(!function_exists('zt_popular_widget')){
	function zt_popular_widget(){
	}
	add_action('wp_ajax_zt_popular_widget', 'zt_popular_widget');
	add_action('wp_ajax_nopriv_zt_popular_widget', 'zt_popular_widget');
}

/* Add Post featured
========================================================
*/
if(!function_exists('zt_add_featured')){
	function zt_add_featured(){
        $postid	 = zeta_isset($_REQUEST,'postid');
		$nonce	 = zeta_isset($_REQUEST,'nonce');
		$newdate = date("Y-m-d H:i:s");
		if($postid AND wp_verify_nonce( $nonce,'zt-featured-'.$postid)) {
            update_post_meta($postid, 'zt_featured_post','1');
            $post = array(
                'ID'                => $postid,
                'post_modified'     => $newdate,
                'post_modified_gmt' => $newdate
            );
            wp_update_post($post);
		}
		die();
	}
	add_action('wp_ajax_zt_add_featured', 'zt_add_featured');
	add_action('wp_ajax_nopriv_zt_add_featured', 'zt_add_featured');
}

/* Delete Post featured
========================================================
*/
if(!function_exists('zt_remove_featured')){
	function zt_remove_featured(){
		$postid	= zeta_isset($_REQUEST,'postid');
		$nonce	= zeta_isset($_REQUEST,'nonce');
		if($postid AND wp_verify_nonce($nonce, 'zt-featured-'.$postid)) {
			delete_post_meta( $postid, 'zt_featured_post');
		}
		die();
	}
	add_action('wp_ajax_zt_remove_featured', 'zt_remove_featured');
	add_action('wp_ajax_nopriv_zt_remove_featured', 'zt_remove_featured');
}

/* Add Post featured
========================================================
*/
if(!function_exists('zt_add_featured_slider')){
	function zt_add_featured_slider(){
        $postid	 = zeta_isset($_REQUEST,'postid');
		$nonce	 = zeta_isset($_REQUEST,'nonce');
		$newdate = date("Y-m-d H:i:s");
		if($postid AND wp_verify_nonce( $nonce,'zt-featured-slider-'.$postid)) {
            update_post_meta($postid, 'zt_featured_slider','1');
            $post = array(
                'ID'                => $postid,
                'post_modified'     => $newdate,
                'post_modified_gmt' => $newdate
            );
            wp_update_post($post);
		}
		die();
	}
	add_action('wp_ajax_zt_add_featured_slider', 'zt_add_featured_slider');
	add_action('wp_ajax_nopriv_zt_add_featured_slider', 'zt_add_featured_slider');
}

/* Delete Post featured
========================================================
*/
if(!function_exists('zt_remove_featured_slider')){
	function zt_remove_featured_slider(){
		$postid	= zeta_isset($_REQUEST,'postid');
		$nonce	= zeta_isset($_REQUEST,'nonce');
		if($postid AND wp_verify_nonce($nonce, 'zt-featured-slider-'.$postid)) {
			delete_post_meta( $postid, 'zt_featured_slider');
		}
		die();
	}
	add_action('wp_ajax_zt_remove_featured_slider', 'zt_remove_featured_slider');
	add_action('wp_ajax_nopriv_zt_remove_featured_slider', 'zt_remove_featured_slider');
}



/* Filter all content
========================================================
*/
if(!function_exists('zt_social_count')) {
	function zt_social_count() {
		$idpost = zeta_isset($_POST,'id');
		$meta   = get_post_meta($idpost,'zt_social_count',true);
        $meta   = isset($meta) ? $meta : '0';
		$meta++;
		update_post_meta( $idpost,'zt_social_count', $meta );
		echo zeta_comvert_number($meta);
		die();
	}
	add_action('wp_ajax_zt_social_count', 'zt_social_count');
	add_action('wp_ajax_nopriv_zt_social_count', 'zt_social_count');
}


/* Delete count report
========================================================
*/
if(!function_exists('delete_notice_report')) {
	function delete_notice_report() {
		$id = zeta_isset($_GET,'id');
		if(current_user_can('administrator')) {
			update_post_meta($id,'numreport','0');
		}
		wp_redirect($_SERVER['HTTP_REFERER'], 302); exit;
	}
	add_action('wp_ajax_delete_notice_report', 'delete_notice_report');
	add_action('wp_ajax_nopriv_delete_notice_report', 'delete_notice_report');
}