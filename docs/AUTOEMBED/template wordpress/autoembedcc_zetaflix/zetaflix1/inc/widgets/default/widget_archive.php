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

?>
<div class="sidebar-module">
	<div class="sidebar-title"><span><?php _z('Genres');?></span></div>
	<div class="sidebar-content">
		<div class="genre-listing">
			<ul class="genre-list scrolling">
				<?php zeta_li_genres(10);?>
			</ul>
		</div>
	</div>
</div>

<div class="sidebar-module">
	<div class="sidebar-content">
			
		<div class="random-content vertical">
		<?php global $post;
				$tags = wp_get_post_terms($post->ID, 'genres');
				if ($tags) {
					$first_tag 	= isset($tags[0]) ? $tags[0]->term_id : false;
					$second_tag = isset($tags[1]) ? $tags[1]->term_id : false;
					$third_tag 	= isset($tags[2]) ? $tags[2]->term_id : false;
					$ptype		= isset(get_queried_object()->name) ? get_queried_object()->name : null;
					$posttype	= (is_archive() && $ptype) ? $ptype : array('movies', 'tvshows');
					$args = array(
						'post_type' => $posttype,
						'posts_per_page' => 8,
						'orderby' => 'rand'
					);
					$related = get_posts($args);
					$i = 0;
					if($related){
						global $post;
						$temp_post = $post;
							foreach($related as $post) : setup_postdata($post);
								get_template_part('inc/parts/item_widget_related');
							endforeach;
						$post = $temp_post;
					}
				} ?>
		</div>
	</div>
</div>

<div class="sidebar-module">
	<div class="sidebar-title"><span><?php _z('Year');?></span></div>
	<div class="sidebar-content">
		<div class="year-listing">
			<ul class="year-list scrolling">
				<?php zeta_release_years();?>
			</ul>
		</div>
	</div>
</div>