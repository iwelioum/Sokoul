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

class ZT_Widget_related extends WP_Widget {

	function __construct() {
		$widget_ops = array('classname' => 'zetathemes_widget', 'description' => __z('A widget to show related content in the sidebar') );
		$control_ops = array('width' => 300, 'height' => 350, 'id_base' => 'ztw_content_related');
		parent::__construct('ztw_content_related', __z('ZetaFlix - Stream Related'), $widget_ops, $control_ops );
	}

	function widget( $args, $instance ) {
		extract( $args );
		//Our variables from the widget settings.

		$title  = apply_filters('widget_title', zeta_isset($instance,'title'));
		$num    = zeta_isset($instance,'zt_nun');
		$order  = zeta_isset($instance,'zt_order');
		$layout = zeta_isset($instance,'zt_layout');
		$rand   = zeta_isset($instance,'zt_rand') ? 'rand' : 'false';
		$class = ($layout == 'a') ? 'horizontal' : 'vertical';
		echo $before_widget;
		
		
		if(is_singular(array('movies','tvshows'))):
		
		// Display Widget title
		if($title) echo $before_title . $title . $after_title;
		echo '<div class="sidebar-content">';
		
		echo '<div class="related-content '.$class.'">';
		global $post;
		$tags = wp_get_post_terms($post->ID, 'genres');
		if ($tags) {
			$first_tag 	= isset($tags[0]) ? $tags[0]->term_id : false;
			$second_tag = isset($tags[1]) ? $tags[1]->term_id : false;
			$third_tag 	= isset($tags[2]) ? $tags[2]->term_id : false;
			$args = array(
				'post_type' => get_post_type($post->ID),
				'posts_per_page' => $num,
				'orderby' => $rand,
				'order' => $order,
				'tax_query' => array(
					'relation' => 'OR',
					array(
						'taxonomy' => 'genres',
						'terms' => $second_tag,
						'field' => 'id',
						'operator' => 'IN',
					),
					array(
						'taxonomy' => 'genres',
						'terms' => $first_tag,
						'field' => 'id',
						'operator' => 'IN',
					),
					array(
						'taxonomy' => 'genres',
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
					foreach($related as $post) : setup_postdata($post);
						get_template_part('inc/parts/item_widget_related');
					endforeach;
				$post = $temp_post;
			}
		}
		echo '</div>';
		echo '</div>';
		//Display Query posts
		// End Query
		
		
		//else:		
		
		//	echo '<span class="error-widget"><strong>['.__z('ZetaFlix - Stream Related').']</strong>';
		//	echo __z('Error: Only for movies and tvshows');		
		//	echo '</span>';
			
		endif;

		
		echo $after_widget;
	}
	//Update the widget
	function update( $new_instance, $old_instance ) {
		$instance = $old_instance;
		//Strip tags from title and name to remove HTML
		$instance['title']     = strip_tags( $new_instance['title'] );
		$instance['zt_nun']    = strip_tags( $new_instance['zt_nun'] );
		$instance['zt_order']  = strip_tags( $new_instance['zt_order'] );
		$instance['zt_rand']   = strip_tags( $new_instance['zt_rand'] );
		$instance['zt_layout'] = strip_tags( $new_instance['zt_layout'] );
		return $instance;
	}
	function form( $instance ) {
		//Set up some default widget settings.
		$defaults = array('title' => '', 'zt_nun' => '10',  'zt_order' => 'desc', 'zt_layout' => 'b', 'zt_rand' => 'rand');
		$instance = wp_parse_args( (array) $instance, $defaults ); ?>
		<p>
			<label for="<?php echo $this->get_field_id('title'); ?>"><?php _z('Title:'); ?></label>
			<input id="<?php echo $this->get_field_id('title'); ?>" name="<?php echo $this->get_field_name('title'); ?>" value="<?php echo $instance['title']; ?>" style="width:100%;" />
		</p>
		<p>
			<label for="<?php echo $this->get_field_id('zt_layout'); ?>"><?php _z('Display'); ?></label>
			<select id="<?php echo $this->get_field_id('zt_layout'); ?>" name="<?php echo $this->get_field_name('zt_layout'); ?>" style="width:100%;">
				<option <?php if ('a' == $instance['zt_layout'] ) echo 'selected="selected"'; ?> value="a"><?php _z('Horizontal Poster'); ?></option>
				<option <?php if ('b' == $instance['zt_layout'] ) echo 'selected="selected"'; ?> value="b"><?php _z('Vertical Poster'); ?></option>
            </select>
		</p>
		<p>
			<label for="<?php echo $this->get_field_id('zt_nun'); ?>"><?php _z('Items number'); ?></label>
			<input type="number" id="<?php echo $this->get_field_id('zt_nun'); ?>" name="<?php echo $this->get_field_name('zt_nun'); ?>" value="<?php echo $instance['zt_nun']; ?>" min="1" max="20" style="width:100%;">
		</p>
		<p>
			<label for="<?php echo $this->get_field_id('zt_order'); ?>"><?php _z('Content order'); ?></label>
			<select id="<?php echo $this->get_field_id('zt_order'); ?>" name="<?php echo $this->get_field_name('zt_order'); ?>" class="widefat" style="width:100%;">
				<option <?php if ('desc' == $instance['zt_order'] ) echo 'selected="selected"'; ?> value="desc"><?php _z('Descending'); ?></option>
				<option <?php if ('asc' == $instance['zt_order'] ) echo 'selected="selected"'; ?> value="asc"><?php _z('Ascending'); ?></option>
            </select>
		</p>
		<p>
			<input class="checkbox" type="checkbox" <?php checked( $instance[ 'zt_rand' ], 'on'); ?> id="<?php echo $this->get_field_id('zt_rand'); ?>" name="<?php echo $this->get_field_name('zt_rand'); ?>" />
			<label for="<?php echo $this->get_field_id('zt_rand'); ?>"> <?php _z('Activate random order'); ?></label>
		</p>
	<?php
	}
}
