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

class ZT_Widget_blog_recent extends WP_Widget {

	function __construct() {
		$widget_ops = array('classname' => 'zetathemes_widget', 'description' => __z('Show list of recent blog posts') );
		$control_ops = array('width' => 300, 'height' => 350, 'id_base' => 'ztw_blogrecent');
		parent::__construct('ztw_blogrecent', __z('ZetaFlix - Blog Recent'), $widget_ops, $control_ops );
	}

	function widget( $args, $instance ) {
		extract( $args );
		//Our variables from the widget settings.
		
		$title = (isset($instance['title'])) ? $instance['title'] : '';
		$title  = apply_filters('widget_title', $title);
		$num    = zeta_isset($instance,'zt_nun');
		$thumb  = zeta_isset($instance,'zt_thumb') ? 'thumb' : 'false';
		
		$blog = zeta_get_option('pageblog');
		
		echo $before_widget;		
			
		// Display Widget title
		
		if($thumb === 'thumb') $class  = 'thumb';
		if($title) echo $before_title . $title . $after_title;
		
		echo '<div class="sidebar-content">';
		echo '<div class="blog-recent '.$thumb.'">';
        echo '<ul class="recent-list">';
		
		$args = array(		
			'posts_per_page' => $num,
			'offset' => 0,
			'orderby' => 'post_date',
			'order' => 'DESC',
			'post_type' => 'post',
			'post_status' => 'publish'
		);
		
		
		$query = new WP_Query($args);
		if ($query->have_posts()) :
        while ($query->have_posts()) : $query->the_post();
			if($thumb === 'thumb'){
				$img 	= '<img src="http://127.0.0.1/zt/wp-content/uploads/2022/05/vQkUhrMhRZ8Ox08qYDdy9Npj69h.jpg">'; 
				$url 	= '<a class="original-url" href="'.get_the_permalink().'"></a>';
			}else{
				$url = 'href="'.get_the_permalink().'"';
			}
			echo '<li>';
			echo ($thumb === 'thumb') ? $url : null;
			echo '<div class="recent-list-data">';			
			echo '<a '.$url.'>'.get_the_title().'</a>';
			echo '<span class="post-date"><i class="fa-solid fa-calendar-days"></i> '.get_the_date().'</span>';
			echo '</div>';
			echo ($thumb === 'thumb') ? $img : null;
			echo '</li>';
		endwhile;
		endif;
        echo '</ul>';
		echo '</div>';	
		echo '</div>';		
	
		
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
		$defaults = array('title' => 'Recent Posts', 'zt_nun' => '10');
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
