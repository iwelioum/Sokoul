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

class ZT_Widget extends WP_Widget {

	function __construct() {
		$widget_ops = array('classname' => 'zetathemes_widget', 'description' => __z('A widget to show content in the sidebar') );
		$control_ops = array('width' => 300, 'height' => 350, 'id_base' => 'ztw_content');
		parent::__construct('ztw_content', __z('ZetaFlix - Sidebarz content'), $widget_ops, $control_ops );
	}

	function widget( $args, $instance ) {
		extract( $args );
		//Our variables from the widget settings.

		$title = apply_filters('widget_title', $instance['title'] );
		$num    = $instance['zt_nun'];
		$order  = $instance['zt_order'];
		$layout = $instance['zt_layout'];
		$tipo   = $instance['zt_tipo'] == 'movies_shows' ? array('movies','tvshows') : $instance['zt_tipo'];
		$rand   = $instance[ 'zt_rand' ] ? 'rand' : 'false';

		echo $before_widget;
		// Display Widget title
		if ( $title )
			echo $before_title . $title . $after_title;
		//Display Query posts
		echo '<div class="dtw_content">';
	query_posts( array('post_type' => $tipo, 'showposts' => $num, 'orderby' => $rand, 'order' => $order ));
	while ( have_posts() ) : the_post();
		get_template_part('inc/parts/item_widget_'. $layout .'');
	endwhile; wp_reset_query();
		echo '</div>';
		// End Query
		echo $after_widget;
	}
	//Update the widget
	function update( $new_instance, $old_instance ) {
		$instance = $old_instance;
		//Strip tags from title and name to remove HTML
		$instance['title']     = strip_tags( zeta_isset($new_instance,'title'));
		$instance['zt_nun']    = strip_tags( zeta_isset($new_instance,'zt_nun'));
		$instance['zt_order']  = strip_tags( zeta_isset($new_instance,'zt_order'));
		$instance['zt_rand']   = strip_tags( zeta_isset($new_instance,'zt_rand'));
		$instance['zt_layout'] = strip_tags( zeta_isset($new_instance,'zt_layout'));
		$instance['zt_tipo']   = zeta_isset($new_instance,'zt_tipo');
		return $instance;
	}
	function form( $instance ) {
		//Set up some default widget settings.
		$defaults = array('title' => '', 'zt_nun' => '10', 'zt_tipo' => 'movies', 'zt_order' => 'desc', 'zt_layout' => 'wa', 'zt_rand' => 'false');
		$instance = wp_parse_args( (array) $instance, $defaults ); ?>
		<p>
			<label for="<?php echo $this->get_field_id('title'); ?>"><?php _z('Title:'); ?></label>
			<input id="<?php echo $this->get_field_id('title'); ?>" name="<?php echo $this->get_field_name('title'); ?>" value="<?php echo $instance['title']; ?>" style="width:100%;" />
		</p>
		<p>
			<label for="<?php echo $this->get_field_id('zt_layout'); ?>"><?php _z('Layout style'); ?></label>
			<select id="<?php echo $this->get_field_id('zt_layout'); ?>" name="<?php echo $this->get_field_name('zt_layout'); ?>" style="width:100%;">
				<option <?php if ('wa' == $instance['zt_layout'] ) echo 'selected="selected"'; ?> value="wa"><?php _z('Style 1 - image Backdrop'); ?></option>
				<option <?php if ('wb' == $instance['zt_layout'] ) echo 'selected="selected"'; ?> value="wb"><?php _z('Style 2 - image Poster'); ?></option>
				<option <?php if ('wc' == $instance['zt_layout'] ) echo 'selected="selected"'; ?> value="wc"><?php _z('Style 3 - no image'); ?></option>
            </select>
		</p>
		<p>
			<label for="<?php echo $this->get_field_id('zt_tipo'); ?>"><?php _z('Content type'); ?></label>
			<select id="<?php echo $this->get_field_id('zt_tipo'); ?>" name="<?php echo $this->get_field_name('zt_tipo'); ?>" style="width:100%;">
				<option <?php if ('movies' == $instance['zt_tipo'] ) echo 'selected="selected"'; ?> value="movies"><?php _z('Movies'); ?></option>
				<option <?php if ('tvshows' == $instance['zt_tipo'] ) echo 'selected="selected"'; ?> value="tvshows"><?php _z('TV Shows'); ?></option>
				<option <?php if ('episodes' == $instance['zt_tipo'] ) echo 'selected="selected"'; ?> value="episodes"><?php _z('Episodes'); ?></option>
				<option <?php if ('movies_shows' == $instance['zt_tipo'] ) echo 'selected="selected"'; ?> value="movies_shows"><?php _z('Movies and Shows'); ?></option>
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
