<?php
/*
* -------------------------------------------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @aopyright: (c) 2023 Zetathemes. All rights reserved
* -------------------------------------------------------------------------------------
*
* @since 1.0.0
*
*/
$posterStyle = zeta_get_option('poster_style','horizontal');
$similarStyle = zeta_get_option('similar_style', 'vertical');
$posterStyle = ($similarStyle == 'vertical') ? 'Vertical' : $posterStyle;
$posterSource = zeta_get_option('poster_source','meta');


$similar = zeta_get_option('similar_module');
$similarStyle = zeta_get_option('similar_style','fixed');
$similarClass = ($similarStyle == 'fixed') ? ' '.$similarStyle : ' '.$similarStyle;

$posttype = get_post_type(get_the_id());
$posttype = ($posttype != 'tvshows') ? $posttype : 'tvshows';

$related = '';

if($similar){


	
    // Related content ( 3 relations )
	echo '<div class="content-similar'.$similarClass.'">';
	echo '<div class="content-title"><span class="title-head">'. __z('You May Also Like'). '</span></div>';
	echo '<div class="similar-wrapper">';
		
		
		if($posttype == 'episodes' || $posttype == 'seasons'){
			$postmeta = zeta_postmeta_tvshows($post->ID);
			switch($posttype){
				case 'episodes':
				$postmeta = zeta_postmeta_episodes($post->ID);
				case 'seasons':
				$postmeta = zeta_postmeta_seasons($post->ID);
			}
			$rid = zeta_isset($postmeta,'tvshowid');
			$tags = wp_get_post_terms( $rid, 'genres');
			$posttype = 'tvshows';
		}else{
			$posttype = $posttype;
			$rid = $post->ID;
			$tags = wp_get_post_terms( $post->ID, 'genres');
		}
		
		
		if ($tags) {

			// Get iTags
			$itag[1] = isset( $tags[0] ) ? $tags[0]->term_id : null;
			$itag[2] = isset( $tags[1] ) ? $tags[1]->term_id : null;
			$itag[3] = isset( $tags[2] ) ? $tags[2]->term_id : null;			


			// The Args
			$args = array(
				'post_type'      => $posttype,
				'posts_per_page' => 18,
				//'post__not_in'   => array( $rid ),
				'orderby'        => 'rand',
				'order'          => 'asc',
				// Check relationship
				'tax_query' => array(
					'relation' => 'OR',
					array(
						'taxonomy' => 'genres',
						'terms'    => $itag[1],
						'field'    => 'id',
						'operator' => 'IN'
					),
					array(
						'taxonomy' => 'genres',
						'terms'    => $itag[2],
						'field'    => 'id',
						'operator' => 'IN'
					),
					array(
						'taxonomy' => 'genres',
						'terms'    => $itag[3],
						'field'    => 'id',
						'operator' => 'IN'
					)
				)
			);

			$related = get_posts($args);
			$i = 0;
		}
	
	
	if($similarStyle == 'fixed') {
		
			if( $related ) {
				global $post;
				$temp_post = $post;
				foreach($related as $post) {
					$poster = ($posterStyle == 'Vertical') ? omegadb_get_poster('' , $post->ID) : omegadb_get_poster('', $post->ID, '', 'zt_backdrop', 'w300', $posterSource);
					$year = strip_tags(get_the_term_list($post->ID, 'ztyear'));
					setup_postdata($post);
					// The view
					$show = ( $i > 5 ) ? ' hide' : NULL;
					if($post->ID != $rid){
						echo '<div class="similar-item'.$show.'">';
						echo '<a href="'. esc_url(get_the_permalink( $post->ID )). '" data-url="" class="item-url" data-hasqtip="112" title="'.esc_attr(get_the_title( $post->ID )).'"></a>';
						echo '<div class="item-desc-hover">';
						echo '<div class="item-desc-btn">';
						echo '<span class="add-to-list" title='.__z('Add to List').'"><i class="fas fa-plus"></i></span>';
						echo '</div>';
						//echo '</a>';
						echo '</div>';
						echo '<div class="item-data"><h3>'. get_the_title( $post->ID ). '</h3><span class="item-date">'.$year.'</span></div>';
						echo '<img data-original="'.$poster.'" class="lazy thumb mli-thumb" alt="'. esc_attr(get_the_title( $post->ID )). '" src="'.$poster.'" style="display: inline-block;"><div class="clearfix"></div>';
						echo '</div>';
						$i++;
					}
				}
				if($i > 5) {
					echo '<div class="similar-more">
						<a class="similar-more-btn" title="'.__z("View More").'"><i class="fa-solid fa-angle-down"></i></a>
						<div class="clearfix"></div>
						</div>';
				}
				$post = $temp_post;
			}
			

	}else{

			if( $related ) {
				global $post;
				$temp_post = $post;
				echo '<div class="similar-module featured-movies"><div class="module-content owl-carousel">';
				foreach($related as $post) {
					$poster = ($posterStyle == 'Vertical') ? omegadb_get_poster('' , $post->ID) : omegadb_get_poster('', $post->ID, '', 'zt_backdrop', 'w300', $posterSource);
					$year = strip_tags(get_the_term_list($post->ID, 'ztyear'));
					setup_postdata($post);
					// The view

					if($post->ID != $rid){
						echo '<div id="item-'.$post->ID.'" class="module-item">';
						echo '<a href="'.esc_url(get_permalink($post->ID)).'" data-url="" class="ml-mask jt" data-hasqtip="112" title="'.esc_attr(get_the_title( $post->ID )).'">';
						echo '<span class="item-data"><h3>'. get_the_title( $post->ID ). '</h3><span class="item-date">'.$year.'</span></span>';
						echo '<img data-original="'.$poster.'" class="lazy thumb mli-thumb" alt="'.esc_attr(get_the_title( $post->ID )).'" src="'.$poster.'" style="display: inline-block;">';
						echo '</a>';
						echo '<div class="item-desc"><div class="item-desc-hl"><div class="desc-hl-right">';
						//echo '<span class="item-quality">Ultra 4k</span>';
						//echo '<span class="item-language us">English</span>';
						echo '</div></div></div>';
						echo '<div class="item-desc-hover">';
						zt_useritem_btn($post->ID, $posttype, $post->post_name);
						echo '</div>';
						echo '</div>';
					}
					

				}
				echo '</div></div>';
				$post = $temp_post;
			}
	}
		
	echo '</div></div>';
}
// End Script
