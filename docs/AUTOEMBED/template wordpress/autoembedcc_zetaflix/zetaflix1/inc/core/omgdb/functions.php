<?php
/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
* @since 1.0.0
*/

/**
 * @since 1.0.0
 * @version 1.0
 */ 
function omegadb_get_slide_logo($post_id = ''){ 
	$image_meta = get_post_meta($post_id,'zt_logo',true);	
	if(!empty($image_meta)){
		if(filter_var($image_meta, FILTER_VALIDATE_URL)){
				$slide_logo = '<div class="f-title-img"><img alt="'.get_the_title().'" class="title-logo" src="'.$image_meta.'" title="'.get_the_title().'"></div>';
		}else{
			$slide_logo = '<h1>'.get_the_title().'</h1>';
		}
	}else{
		$slide_logo = '<h1>'.get_the_title().'</h1>';
	}

    return $slide_logo;
}

/**
 * @since 1.0.0
 * @version 1.0
 */ 
function omegadb_get_slide_backdrop($post_id = '', $size = 'w500'){ 
	$image_meta = get_post_meta($post_id,'zt_banner',true);
	$image_meta2 = get_post_meta($post_id,'zt_backdrop',true);
	$backdrop = ZETA_URI.'/assets/img/no/zt_backdrop.png';
	
    if($image_meta && $image_meta != 'null'){
        if(substr($image_meta, 0, 1) == '/'){
            $backdrop = 'https://image.tmdb.org/t/p/'.$size.$image_meta;
        }elseif(filter_var($image_meta, FILTER_VALIDATE_URL)){
            $backdrop = $image_meta;
        }
    }else{
        if(substr($image_meta2, 0, 1) == '/'){
            $backdrop = 'https://image.tmdb.org/t/p/'.$size.$image_meta2;
        }elseif(filter_var($image_meta2, FILTER_VALIDATE_URL)){
            $backdrop = $image_meta2;
        }
	}
    return esc_url($backdrop);
}

/**
 * @since 1.0.0
 * @version 1.0
 */
 
function omegadb_get_poster($post_type = '', $post_id = '', $thumb_size = 'full', $post_meta = 'zt_poster', $size = 'w500', $source = 'meta'){
    switch ($post_type){
        case 'episodes':
            $source = zeta_get_option('poster_source_ep','meta');
            $source = apply_filters('poster_source_ep', $source);
            if($source == 'meta'){
                $post_meta =  zeta_get_option('poster_meta_source_ep','zt_backdrop');
                $post_meta = apply_filters('module_poster_meta_source_ep', $post_meta);
            }
            break;
        case 'seasons':
            $source =  zeta_get_option('poster_source_ss','thumb');
            $source = apply_filters('module_poster_source_ss', $source);
            if($source == 'meta'){
                $post_meta =  zeta_get_option('poster_meta_source_ss');
                $post_meta = apply_filters('module_poster_meta_source_ss', $post_meta);
            }
            break;
        case 'tvshows':
        case 'movies':
            $source =  zeta_get_option('poster_source','thumb');
            $source = apply_filters('module_poster_source', $source);	
            if($source == 'meta'){
                $post_meta =  zeta_get_option('poster_meta_source','zt_poster');
                $post_meta = apply_filters('poster_meta_source', $post_meta);
            }
            break;
    }
	switch ($post_meta){
		case 'zt_poster':
		$size = zeta_get_option('poster_size', 'w500');
		break;
		case 'zt_backdrop':
		$size = zeta_get_option('backdrop_size', 'w780');
		break;
	}


    $thumb_id = get_post_thumbnail_id($post_id);
    $poster   = ZETA_URI.'/assets/img/no/'.$post_meta.'.png';

	if($source == 'meta'){
		$image_meta = get_post_meta($post_id,$post_meta,true);
		if($image_meta && $image_meta != 'null'){
			if(substr($image_meta, 0, 1) == '/'){
				$poster = 'https://image.tmdb.org/t/p/'.$size.$image_meta;
			}elseif(filter_var($image_meta, FILTER_VALIDATE_URL)){
				$poster = $image_meta;
			}
		}
	}else{
		if($thumb_id){
			$thumb_url = wp_get_attachment_image_src($thumb_id, $thumb_size,true);
			$poster = isset($thumb_url[0]) ? $thumb_url[0] : false;
		}else{
			$image_meta = get_post_meta($post_id,$post_meta,true);
			if($image_meta && $image_meta != 'null'){
				if(substr($image_meta, 0, 1) == '/'){
					$poster = 'https://image.tmdb.org/t/p/'.$size.$image_meta;
				}elseif(filter_var($image_meta, FILTER_VALIDATE_URL)){
					$poster = $image_meta;
				}
			}
		}
	}
	
	if($post_type === 'episodes' && $poster === ZETA_URI.'/assets/img/no/'.$post_meta.'.png'){
		$poster = '';
	}
	
	
    return esc_url($poster);
}

/**
 * @since 1.0.0
 * @version 1.0
 */
 


/**
 * @since 1.0.0
 * @version 1.0
 */
function omegadb_get_backdrop($post_type = '', $post_id = '', $size = 'w500', $tvid = '', $ssid = ''){
    $image_meta = get_post_meta($post_id,'zt_backdrop',true);
    $backdrop = ZETA_URI.'/assets/img/no/zt_backdrop.png';
	
	if($post_type == 'ep' || $post_type == 'ss'){
		
		if($image_meta && $image_meta != 'null'){
			if(substr($image_meta, 0, 1) == '/'){
				$backdrop = 'https://image.tmdb.org/t/p/'.$size.$image_meta;
			}elseif(filter_var($image_meta, FILTER_VALIDATE_URL)){
				$backdrop = $post_type;
			}
		}elseif($tvid){
			$image_meta = get_post_meta($tvid,'zt_backdrop',true);
			if($image_meta && $image_meta != 'null'){
				if(substr($image_meta, 0, 1) == '/'){
					$backdrop = 'https://image.tmdb.org/t/p/'.$size.$image_meta;
				}elseif(filter_var($image_meta, FILTER_VALIDATE_URL)){
					$backdrop = $post_type;
				}
			}
		}		
	}else{	
	
		if($image_meta && $image_meta != 'null'){
			if(substr($image_meta, 0, 1) == '/'){
				$backdrop = 'https://image.tmdb.org/t/p/'.$size.$image_meta;
			}elseif(filter_var($image_meta, FILTER_VALIDATE_URL)){
				$backdrop = $post_type;
			}
		}
	}
	

    return esc_url($backdrop);
}

/**
 * @since 1.0.0
 * @version 1.0
 */
function omegadb_get_images($data = ''){
    if($data){
        $ititle = get_the_title();
        $images = explode("\n", $data);
        $icount = 0;
        $out_html = "<div id='zt_galery' class='galeria'>";
        foreach($images as $image) if($icount < 10){
            if(!empty($image)){
                if(substr($image, 0, 1) == '/'){
                    $out_html .= "<div class='g-item'>";
                    $out_html .= "<a href='https://image.tmdb.org/t/p/original{$image}' title='{$ititle}'>";
                    $out_html .= "<img src='https://image.tmdb.org/t/p/w300{$image}' alt='{$ititle}'>";
                    $out_html .= "</a></div>";
                }else{
                    $out_html .= "<div class='g-item'>";
                    $out_html .= "<a href='{$image}' title='{$ititle}'>";
                    $out_html .= "<img src='{$image}' alt='{$ititle}'>";
                    $out_html .= "</a></div>";
                }
            }
            $icount++;
        }
        $out_html .= "</div>";
        // The View
        echo $out_html;
    }
}

/**
 * @since 1.0.0
 * @version 1.0
 */
function omegadb_get_rand_image($data = ''){
    if($data){
        $urlimg = '';
        $images = explode("\n", $data);
        $icount = array_rand($images);
        if(!empty($images[$icount])){
            $image = $images[$icount];
        }else{
            $image = $images[0];
        }
        if(substr($image, 0, 1) == '/'){
            $urlimg = 'https://image.tmdb.org/t/p/original'.$image;
        }elseif(filter_var($image,FILTER_VALIDATE_URL)){
            $urlimg = $image;
        }
        if(!empty($urlimg)){
            return esc_url($urlimg);
        }
    }
}

function omegadb_get_gallery_images($data = ''){
    if($data){
        $ititle = get_the_title();
        $images = explode("\n", $data);
        $icount = 0;
        $out_html = "<div class='content-gall owl-carousel owl-theme'>";
        foreach($images as $image) if($icount < 10){
            if(!empty($image)){
                if(substr($image, 0, 1) == '/'){
                    $out_html .= "<div class='gall-item'>";
                    $out_html .= "<a href='https://image.tmdb.org/t/p/original{$image}' title='{$ititle}'>";
                    $out_html .= "<img src='https://image.tmdb.org/t/p/w300{$image}' alt='{$ititle}'>";
                    $out_html .= "</a></div>";
                }else{
                    $out_html .= "<div class='gall-item'>";
                    $out_html .= "<a href='{$image}' title='{$ititle}'>";
                    $out_html .= "<img src='{$image}' alt='{$ititle}'>";
                    $out_html .= "</a></div>";
                }
            }
            $icount++;
        }
        $out_html .= "</div><div class='clear'></div>";
        // The View
        echo $out_html;
    }
}

/**
 * @since 1.0.0
 * @version 1.0
 */
function omegadb_title_tags($option, $data){
    $option = str_replace('{name}', zeta_isset($data,'name'),$option);
    $option = str_replace('{year}', zeta_isset($data,'year'),$option);
    $option = str_replace('{season}', zeta_isset($data,'season'),$option);
    $option = str_replace('{episode}', zeta_isset($data,'episode'),$option);
    return apply_filters('omegadb_title_tags',$option);
}

/**
 * @since 1.0
 * @version 1.0
 */
function omegadb_clean_tile($tmdb){
    $files = glob(OMEGADB_CACHE_DIR.'*.'.$tmdb);
    if(!empty($files)){
        foreach($files as $file){
            if(is_file($file)) unlink($file);
        }
    }
}

/**
 * @since 1.0
 * @version 1.0
 */
if(!function_exists('omegadb_clean_tile_expired')){
    function omegadb_clean_tile_expired(){
        foreach(glob(OMEGADB_CACHE_DIR."*") as $file){
            if(is_file($file) && (filemtime($file) + OMEGADB_CACHE_TIM <= time())) unlink($file);
        }
    }
    // Verificator
    if(!wp_next_scheduled('omegadb_clean_cache_expires')) {
        wp_schedule_event(time(),'daily','omegadb_clean_cache_expires');
    }
    // Schedule Action
    add_action('omegadb_clean_cache_expires','omegadb_clean_tile_expired');
}

/**
 * @since 1.0.0
 * @version 1.0
 */
 
function zetaflix_get_blog_thumb($postid){
	if($postid){
			$poster = get_the_post_thumbnail($postid);
			if(!$poster){
				$poster  = '<img src="'.ZETA_URI.'/assets/img/no/zt_backdrop.png">';
			}
	}
	return $poster;
}

add_filter('query_vars', 'add_my_var');
function add_my_var($public_query_vars) {
    $public_query_vars[] = 'watch';
	$public_query_vars[] = 'season';
	$public_query_vars[] = 'episode';
    return $public_query_vars;
}

function custom_rewrite() {

   add_rewrite_rule('movies/(.+?)/watch/(.+?)/?$', 'index.php?movies=$matches[1]&watch=$matches[2]', 'top');
   add_rewrite_rule('tvshows/(.+?)/season/(.+?)/?$', 'index.php?tvshows=$matches[1]&season=$matches[2]', 'top');
   add_rewrite_rule('tvshows/(.+?)/season/(.+?)/episode/(.+?)/?$', 'index.php?tvshows=$matches[1]&season=$matches[2]&episode=$matches[3]', 'top');
   add_rewrite_rule('tvshows/(.+?)/season/(.+?)/episode/(.+?)/watch/(.+?)/?$', 'index.php?tvshows=$matches[1]&season=$matches[2]&episode=$matches[3]&watch=$matches[4]', 'top');
   add_rewrite_rule('seasons/(.+?)/episode/(.+?)/?$', 'index.php?seasons=$matches[1]&episode=$matches[2]', 'top');
   add_rewrite_rule('seasons/(.+?)/episode/(.+?)/watch/(.+?)/?$', 'index.php?seasons=$matches[1]&episode=$matches[2]&watch=$matches[3]', 'top');
   add_rewrite_rule('episodes/(.+?)/watch/(.+?)/?$', 'index.php?episodes=$matches[1]&watch=$matches[2]', 'top');

}
add_action( 'init', 'custom_rewrite' );


//add_action('init', 'wpse42279_add_endpoints');
function wpse42279_add_endpoints()
{
    add_rewrite_endpoint('season', EP_PERMALINK | EP_PAGES);
	add_rewrite_endpoint('episode', EP_PERMALINK | EP_PAGES);
	add_rewrite_endpoint('watch', EP_PERMALINK | EP_PAGES);
	
	add_rewrite_rule(
        'tvshows/(.+?)/season/(.+?)/episode/(.+?)/watch/(.+?)/?$',
        'index.php?tvshows=$matches[1]&season=$matches[2]&episodes=$matches[3]&watch=$matches[4]',
        'top'
    );
	
	

}

function update_tvss_ids( $post_id ) {   	

	
		global $wpdb;

		if('seasons' == get_post_type() || 'episodes' == get_post_type()){

			$tmdbid = get_post_meta($post_id, 'ids', true);		
			if('episodes'){
				$seasonid = get_post_meta($post_id, 'temporada', true);
			}

			
			if(is_numeric($tmdbid)){
				$qry  = "SELECT P.ID FROM ".$wpdb->posts." P, ".$wpdb->postmeta." I WHERE 
				I.meta_key = 'ids' AND I.meta_value = '".$tmdbid."' AND
				P.ID = I.post_id AND P.post_status = 'publish'  AND P.post_type = 'tvshows'";	
				$tvshow = $wpdb->get_row($qry);
				if($tvshow){
					update_post_meta($post_id, 'tvshowid', $tvshow->ID);
				}
				
				if('episodes' == get_post_type() && is_numeric($seasonid)){
					$qry  = "SELECT P.ID FROM ".$wpdb->posts." P, ".$wpdb->postmeta." I, ".$wpdb->postmeta." T WHERE 
					I.meta_key = 'ids' AND I.meta_value = '".$tmdbid."' AND  
					T.meta_key = 'temporada' AND T.meta_value = '".$seasonid."' AND
					P.ID = I.post_id AND P.ID = T.post_id AND
					P.post_status = 'publish'  AND P.post_type = 'seasons'";	
					$season = $wpdb->get_row($qry);
					if($season){
						update_post_meta($post_id, 'seasonid', $season->ID);
					}
				}				
			}
		
		}
	
}
add_action( 'save_post', 'update_tvss_ids' );


function omgdb_body_class($classes) {
    global $post;
    $current_screen = get_current_screen();

    if($current_screen->base === "toplevel_page_omgdb") {
        $classes .= ' omgdb';
    }

    return $classes;
}
add_filter('admin_body_class', 'omgdb_body_class');
