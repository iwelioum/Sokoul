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


/* User likes
========================================================
*/

if(!function_exists('zt_process_list')) {
	function zt_process_list(){
		$post_users = false;
		$nonce		= isset( $_REQUEST['nonce'] ) ? sanitize_text_field( $_REQUEST['nonce'] ) : 0;
		$total		= isset( $_REQUEST['total'] ) ? true : false;
		if( isset( $_REQUEST['post_id'] ) AND wp_verify_nonce( $nonce, 'zt-list-noce') AND is_user_logged_in() ) {
			$post_id = isset($_REQUEST['post_id']) ? $_REQUEST['post_id'] : null;
			if ( !zt_already_listed( $post_id ) ) {
				$user_id	= get_current_user_id();
				$post_users = zt_get_user_lists( $user_id, $post_id );

				$user_list_count = get_user_option("user_list_count", $user_id );
				$user_list_count =  ( isset( $user_list_count ) && is_numeric( $user_list_count ) ) ? $user_list_count : 0;
				update_user_option( $user_id, "user_list_count", ++$user_list_count );

				if ( $post_users ) {
					update_post_meta( $post_id, "_zt_list_users", $post_users );
				}
			} else {
				$user_id = get_current_user_id();
				$post_users = zt_get_user_lists( $user_id, $post_id );

				$user_list_count = get_user_option("user_list_count", $user_id );
				$user_list_count =  ( isset( $user_list_count ) && is_numeric( $user_list_count ) ) ? $user_list_count : 0;
				if ( $user_list_count > 0 ) {
					update_user_option( $user_id, 'user_list_count', --$user_list_count );
				}

				if ( $post_users ) {
					$uid_key = array_search( $user_id, $post_users );
					unset( $post_users[$uid_key] );
					update_post_meta( $post_id, "_zt_list_users", $post_users );
				}
			}
			$meta = get_post_meta( $post_id, "_zt_list_users", TRUE);
			$count = count($meta, COUNT_RECURSIVE);
			$usertotal = get_user_option("user_list_count", $user_id );
			if ($total == true ) echo $usertotal;
			if($total == false) echo $count;
		}
		die();
	}
	add_action('wp_ajax_nopriv_zt_process_list', 'zt_process_list');
	add_action('wp_ajax_zt_process_list', 'zt_process_list');
}

// The button(1)
if(!function_exists('zt_user_buttons')){
	function zt_useritem_btn($post_id, $posttype = '', $postname = '', $page = '', $author = ''){
        if(ZETA_THEME_USER_MOD == true) {
			
			$guest = (!is_user_logged_in()) ? 'clicklogin' : null;
			
			$added_l = (zt_already_listed( $post_id )) ? true : false;
			$added_s = (zt_already_viewed( $post_id )) ? true : false;
			
			//list
			if($page == 'profile-list'){
				$nonce_all = wp_create_nonce('zt-list-noce');
			}elseif($page == 'profile-seen'){
				$nonce_all = wp_create_nonce('zt-view-noce');
			}else{
				$nonce_l = wp_create_nonce('zt-list-noce');
				$tooltip_l = ( $added_l == true ) ? __z('Remove of List') : __z('Add to List');
				$class_l = ( $added_l == true ) ? 'added' : null;
				$process_l = ($guest) ? $guest : 'add-to-list';		
				//seen 
				$nonce_s = wp_create_nonce('zt-view-noce'); 
				$class_s = ( $added_s == true ) ? 'added' : null;
				$tooltip_s = ( $added_s == true ) ? __z('Remove of Seen') : __z('Mark as Seen');
				$process_s = ($guest) ? $guest : 'add-to-seen';	
				
			}			
			

			//$title = the_title_attribute(array('echo' => false, 'post' => $post_id));
			echo '<div class="item-desc-btn">';
			if($page === 'profile-list' || $page === 'profile-seen'){
				if(get_current_user_id() == $author) {
					$class_p = ($page == 'profile-seen') ? 'user_seen_control' : 'user_list_control';
					echo '<a class="'.$class_p.'" data-itemid="'.$post_id.'" data-nonce="'.$nonce_all.'" title="'.__z("Remove").'"><span><i class="fa-solid fa-xmark"></i></span></a>';
				}
				echo '<a href="'.get_permalink($post_id).'" title="'.__z("View").'"><span><i class="fa-solid fa-play"></i></span></a>';
			}else{
					echo ($posttype != 'episodes') ? '<span data-itemid="'.$post_id.'" data-nonce="'.$nonce_l.'" data-itemtype="'.$posttype.'" data-itemname="'.$postname.'" class="'.$process_l.' '.$class_l.'"  title="'.$tooltip_l.'"><i class="fas list"></i></span>' : null;
				if(!is_single()){
					echo ($posttype != 'episodes') ? '<span data-itemid="'.$post_id.'" data-nonce="'.$nonce_s.'" data-itemtype="'.$posttype.'" data-itemname="'.$postname.'" class="'.$process_s.' '.$class_s.'" title="'.$tooltip_s.'"><i class="fas seen"></i></span>' : null;
				}

			}
			echo '</div>';

        }
    }
}


// The button(1)
if(!function_exists('zt_list_button')){
	function zt_list_button($post_id){
        if(ZETA_THEME_USER_MOD == true) {
    		$nonce = wp_create_nonce('zt-list-noce');
    		$class = ( zt_already_listed( $post_id ) ) ? ' in-list' : false;
    		$tooltip = ( zt_already_listed( $post_id ) ) ? __z('Remove of favorites') : __z('Add to favorites');
    		$tooltiphtml = ( !wp_is_mobile() ) ? '<div class="tooltiptext tooltip-right">'.$tooltip.'</div>' : null;
    		$meta = get_post_meta( $post_id, "_zt_list_users", TRUE);
    		$count =  ($meta != null) ? count($meta, COUNT_RECURSIVE) : 0;
    		$process = (is_user_logged_in()) ? 'process_list' : 'btn-login';


    		echo '<a class="'.$process.$class.' tooltip" data-post-id="'. $post_id.'" data-nonce="'.$nonce.'"><i class="ucico fas fa-plus-circle"></i> <span class="list-count-'.$post_id.'">'.$count.'</span>'.$tooltiphtml.'</a>';
        }
    }
}


// Verify POST
if(!function_exists('zt_already_listed')){
	function zt_already_listed($post_id){
		$post_users = null;
		$user_id	= null;
		if ( is_user_logged_in() ) {
			$user_id = get_current_user_id();
			$post_meta_users = get_post_meta( $post_id, "_zt_list_users");
			if ( count( $post_meta_users ) != 0 ) {
				$post_users = $post_meta_users[0];
			}
		}
		if ( is_array( $post_users ) && in_array( $user_id, $post_users ) ) {
			return true;
		} else {
			return false;
		}
	}
}

// Get user listed
if(!function_exists('zt_get_user_lists')){
	function zt_get_user_lists($user_id, $post_id){
		$post_users = '';
		$post_meta_users = get_post_meta( $post_id, "_zt_list_users");
		if ( count( $post_meta_users ) != 0 ) {
			$post_users = $post_meta_users[0];
		}
		if ( !is_array( $post_users ) ) {
			$post_users = array();
		}
		if ( !in_array( $user_id, $post_users ) ) {
			$post_users['u' . $user_id . 'r'] = $user_id;
		}
		return $post_users;
	}
}




/* User views
========================================================
*/
if(!function_exists('zt_process_views')){
	function zt_process_views(){
		$post_users = null;
		$nonce		= isset( $_REQUEST['nonce'] ) ? sanitize_text_field( $_REQUEST['nonce'] ) : 0;
		$total		= isset( $_REQUEST['total'] ) ? true : false;
		if( isset( $_REQUEST['post_id'] ) AND wp_verify_nonce( $nonce, 'zt-view-noce') AND is_user_logged_in() ) {
			$post_id = isset($_REQUEST['post_id']) ? $_REQUEST['post_id'] : null;
			if ( !zt_already_viewed( $post_id ) ) {
				$user_id	= get_current_user_id();
				$post_users = zt_get_user_views( $user_id, $post_id );

				$user_view_count = get_user_option("user_view_count", $user_id );
				$user_view_count =  ( isset( $user_view_count ) && is_numeric( $user_view_count ) ) ? $user_view_count : 0;
				update_user_option( $user_id, "user_view_count", ++$user_view_count );

				if ( $post_users ) {
					update_post_meta( $post_id, "_zt_views_users", $post_users );
				}
			} else {
				$user_id = get_current_user_id();
				$post_users = zt_get_user_views( $user_id, $post_id );

				$user_view_count = get_user_option("user_view_count", $user_id );
				$user_view_count =  ( isset( $user_view_count ) && is_numeric( $user_view_count ) ) ? $user_view_count : 0;
				if ( $user_view_count > 0 ) {
					update_user_option( $user_id, 'user_view_count', --$user_view_count );
				}

				if ( $post_users ) {
					$uid_key = array_search( $user_id, $post_users );
					unset( $post_users[$uid_key] );
					update_post_meta( $post_id, "_zt_views_users", $post_users );
				}
			}
			$meta = get_post_meta( $post_id, "_zt_views_users", TRUE);
			$count = count($meta, COUNT_RECURSIVE);
			$usertotal = get_user_option("user_view_count", $user_id );
			if ($total == true ) echo $usertotal;
			if ($total == false ) echo $count;
		}
		die();
	}
	add_action('wp_ajax_nopriv_zt_process_views', 'zt_process_views');
	add_action('wp_ajax_zt_process_views', 'zt_process_views');
}

// The button(1)
if(!function_exists('zt_views_button')){
	function zt_views_button($post_id){
        if( ZETA_THEME_USER_MOD == true ) {
    		$nonce = wp_create_nonce('zt-view-noce');
    		$class = ( zt_already_viewed( $post_id ) ) ? ' in-views' : false;
    		$tooltip = ( zt_already_viewed( $post_id ) ) ? __z('Remove') : __z('I saw it');
    		$tooltiphtml = ( !wp_is_mobile() ) ? '<div class="tooltiptext tooltip-right">'.$tooltip.'</div>' : null;
    		$meta = get_post_meta( $post_id, "_zt_views_users", TRUE);
    		$count =  ($meta != null) ? count($meta, COUNT_RECURSIVE) : 0;
    		$process = (is_user_logged_in()) ? 'process_views' : 'btn-login';
    		echo '<a class="'.$process.$class.' tooltip" data-post-id="'. $post_id.'" data-nonce="'.$nonce.'"><i class="uvcico fas fa-stream"></i> <span class="views-count-'.$post_id.'">'.$count.'</span>'.$tooltiphtml.'</a>';
    	}
    }
}


// Verify POST
if(!function_exists('zt_already_viewed')){
	function zt_already_viewed($post_id){
		$post_users = null;
		$user_id	= null;
		if ( is_user_logged_in() ) {
			$user_id = get_current_user_id();
			$post_meta_users = get_post_meta( $post_id, "_zt_views_users");
			if ( count( $post_meta_users ) != 0 ) {
				$post_users = $post_meta_users[0];
			}
		}
		if ( is_array( $post_users ) && in_array( $user_id, $post_users ) ) {
			return true;
		} else {
			return false;
		}
	}
}

// Get user listed
if(!function_exists('zt_get_user_views')){
	function zt_get_user_views($user_id, $post_id){
		$post_users = '';
		$post_meta_users = get_post_meta( $post_id, "_zt_views_users");
		if ( count( $post_meta_users ) != 0 ) {
			$post_users = $post_meta_users[0];
		}
		if ( !is_array( $post_users ) ) {
			$post_users = array();
		}
		if ( !in_array( $user_id, $post_users ) ) {
			$post_users['u'.$user_id.'r'] = $user_id;
		}
		return $post_users;
	}
}
