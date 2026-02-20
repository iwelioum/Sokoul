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

class ZT_Widget_random extends WP_Widget {

	function __construct() {
		$widget_ops = array('classname' => 'zetathemes_widget', 'description' => __z('A widget to show random content in the sidebar') );
		$control_ops = array('width' => 300, 'height' => 350, 'id_base' => 'ztw_content_random');
		parent::__construct('ztw_content_random', __z('ZetaFlix - Stream Random'), $widget_ops, $control_ops );
	}

	function widget( $args, $instance ) {
		extract( $args );
		//Our variables from the widget settings.

		$title  = apply_filters('widget_title', zeta_isset($instance,'title'));
		$cntn   = zeta_isset($instance,'zt_content');
		$cntn	= ($cntn != 'movies' && $cntn != 'tvshows') ? array('movies', 'tvshows') : $cntn;
		$num    = zeta_isset($instance,'zt_nun');
		$order  = zeta_isset($instance,'zt_order');
		$layout = zeta_isset($instance,'zt_layout');
		$rand   = zeta_isset($instance,'zt_rand') ? 'rand' : 'false';
		$class = ($layout == 'a') ? 'horizontal' : 'vertical';
      
		echo $before_widget;
      
		if($title) echo $before_title . $title . $after_title;
		echo '<div class="sidebar-content">';
		
		echo '<div class="related-content '.$class.'">';
			$args = array(
				'post_type' => $cntn,
				'posts_per_page' => $num,
				'orderby' => $rand,
				'order' => $order,
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
		echo '</div>';
		echo '</div>';

		echo $after_widget;
	}
	//Update the widget
	function update( $new_instance, $old_instance ) {
		$instance = $old_instance;
		//Strip tags from title and name to remove HTML
		$instance['title']     = strip_tags( $new_instance['title'] );
		$instance['zt_content']    = strip_tags( $new_instance['zt_content'] );
		$instance['zt_nun']    = strip_tags( $new_instance['zt_nun'] );
		$instance['zt_order']  = strip_tags( $new_instance['zt_order'] );
		$instance['zt_rand']   = strip_tags( $new_instance['zt_rand'] );
		$instance['zt_layout'] = strip_tags( $new_instance['zt_layout'] );
		return $instance;
	}
	function form( $instance ) {
		//Set up some default widget settings.
		$defaults = array('title' => '', 'zt_content' => 'all', 'zt_nun' => '10',  'zt_order' => 'desc', 'zt_layout' => 'b', 'zt_rand' => 'on');
		$instance = wp_parse_args( (array) $instance, $defaults ); ?>
		<p>
			<label for="<?php echo $this->get_field_id('title'); ?>"><?php _z('Title:'); ?></label>
			<input id="<?php echo $this->get_field_id('title'); ?>" name="<?php echo $this->get_field_name('title'); ?>" value="<?php echo $instance['title']; ?>" style="width:100%;" />
		</p>
		<p>
			<label for="<?php echo $this->get_field_id('zt_content'); ?>"><?php _z('Content'); ?></label>
			<select id="<?php echo $this->get_field_id('zt_content'); ?>" name="<?php echo $this->get_field_name('zt_content'); ?>" style="width:100%;">
				<option <?php if ('all' == $instance['zt_content'] ) echo 'selected="selected"'; ?> value="all"><?php _z('All'); ?></option>
				<option <?php if ('movies' == $instance['zt_content'] ) echo 'selected="selected"'; ?> value="movies"><?php _z('Movies only'); ?></option>
				<option <?php if ('tvshows' == $instance['zt_content'] ) echo 'selected="selected"'; ?> value="tvshows"><?php _z('TV Shows only'); ?></option>
            </select>
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
			<input class="checkbox" type="checkbox" <?php checked( $instance[ 'zt_rand' ], 'on'); ?> id="<?php echo $this->get_field_id('zt_rand'); ?>" name="<?php echo $this->get_field_name('zt_rand'); ?>" value="on"/>
			<label for="<?php echo $this->get_field_id('zt_rand'); ?>"> <?php _z('Activate random order'); ?></label>
		</p>
	<?php
	}
}
