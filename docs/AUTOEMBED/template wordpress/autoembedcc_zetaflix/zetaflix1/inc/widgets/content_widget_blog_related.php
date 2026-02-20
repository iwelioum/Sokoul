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

class ZT_Widget_blog_related extends WP_Widget {

	function __construct() {
		$widget_ops = array('classname' => 'zetathemes_widget', 'description' => __z('Show related blog posts') );
		$control_ops = array('width' => 300, 'height' => 350, 'id_base' => 'ztw_blogrelated');
		parent::__construct('ztw_blogrelated', __z('ZetaFlix - Blog Related'), $widget_ops, $control_ops );
	}

	function widget( $args, $instance ) {
		extract( $args );
		//Our variables from the widget settings.

		$title  = apply_filters('widget_title', zeta_isset($instance,'title'));
		$num    = zeta_isset($instance,'zt_nun');
		$thumb  = zeta_isset($instance,'zt_thumb') ? 'thumb' : 'false';
		
		
		echo $before_widget;
		// Display Widget title
		
		if(is_singular('post')):
		
		if($thumb === 'thumb') $class  = 'thumb';
		if($title) echo $before_title . $title . $after_title;
		
		echo '<div class="sidebar-content">';
		echo '<div class="blog-recent '.$thumb.'">';
        echo '<ul class="recent-list">';		
		
		$tags = wp_get_post_terms(get_the_ID(), 'category');
		if ($tags) {
			$first_tag 	= isset($tags[0]) ? $tags[0]->term_id : false;
			$second_tag = isset($tags[1]) ? $tags[1]->term_id : false;
			$third_tag 	= isset($tags[2]) ? $tags[2]->term_id : false;
			$args = array(
				'post__not_in' => array(get_the_ID()),
				'post_type' => 'post',
				'posts_per_page' => $num,
				'orderby' => $rand,
				'order' => $order,
				'tax_query' => array(
					'relation' => 'OR',
					array(
						'taxonomy' => 'category',
						'terms' => $second_tag,
						'field' => 'id',
						'operator' => 'IN',
					),
					array(
						'taxonomy' => 'category',
						'terms' => $first_tag,
						'field' => 'id',
						'operator' => 'IN',
					),
					array(
						'taxonomy' => 'category',
						'terms' => $third_tag,
						'field' => 'id',
						'operator' => 'IN',
					)
				)
			);
			$related = get_posts($args);
			$i = 0;
			if($related){
				global $post;
				$temp_post = $post;
					foreach($related as $post) :
						echo '<li>';
						echo '<div class="recent-list-data">';			
						echo '<a href="'.get_permalink().'">'.get_the_title().'</a>';
						echo '<span class="post-date"><i class="fa-solid fa-calendar-days"></i> '.get_the_date().'</span>';
						echo '</div>';
						echo '</li>';						
					endforeach;
				$post = $temp_post;
			}
		}
        echo '</ul>';
		echo '</div>';	
		echo '</div>';		
			
		endif;
		
		echo $after_widget;
	}
	//Update the widget
	function update( $new_instance, $old_instance ) {
		$instance = $old_instance;
		//Strip tags from title and name to remove HTML
		$instance['title']     = strip_tags( $new_instance['title'] );
		$instance['zt_nun']    = strip_tags( $new_instance['zt_nun'] );
		$instance['zt_thumb']   = strip_tags( $new_instance['zt_thumb'] );
		return $instance;
	}
	function form( $instance ) {
		//Set up some default widget settings.
		$defaults = array('title' => 'Related Posts', 'zt_nun' => '10');
		$instance = wp_parse_args( (array) $instance, $defaults ); ?>
		<p>
			<label for="<?php echo $this->get_field_id('title'); ?>"><?php _z('Title:'); ?></label>
			<input id="<?php echo $this->get_field_id('title'); ?>" name="<?php echo $this->get_field_name('title'); ?>" value="<?php echo $instance['title']; ?>" style="width:100%;" />
		</p>
		<p>
			<label for="<?php echo $this->get_field_id('zt_nun'); ?>"><?php _z('Items number'); ?></label>
			<input type="number" id="<?php echo $this->get_field_id('zt_nun'); ?>" name="<?php echo $this->get_field_name('zt_nun'); ?>" value="<?php echo $instance['zt_nun']; ?>" min="1" max="20" style="width:100%;">
		</p>
		<p>
			<input class="checkbox" type="checkbox" <?php checked( $instance[ 'zt_thumb' ], 'on'); ?> id="<?php echo $this->get_field_id('zt_thumb'); ?>" name="<?php echo $this->get_field_name('zt_thumb'); ?>" />
			<label for="<?php echo $this->get_field_id('zt_thumb'); ?>"> <?php _z('Display Thumbnail'); ?></label>
		</p>
	<?php
	}
}
