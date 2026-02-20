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
class ZT_Widget_blog_tags extends WP_Widget {

	public function __construct() {
		$widget_ops = array('classname' => 'zetathemes_widget', 'description' => __z('Tags cloud for Blog posts') );
		$control_ops = array('width' => 300, 'height' => 350, 'id_base' => 'ztw_blogtags');
		parent::__construct('ztw_blogtags', __z('ZetaFlix - Blog Tags'), $widget_ops, $control_ops );
	}

	public function widget( $args, $instance ) {
		extract( $args );
		$title = apply_filters('widget_title',  zeta_isset($instance,'title') );
		$count  = zeta_isset($instance,'zt_count') ? 'count' : 'false';
		// Widget
		
		$blog = zeta_get_option('pageblog');
		if( is_page($blog) || is_singular('post') || is_tag() || is_category() ):
		
		$count = ($count === 'count') ? true : 0;
		
		echo $before_widget;
		if($title)
		echo $before_title . $title . $after_title;
		echo '<div class="sidebar-content">';
		echo '<div class="tag-cloud">';
		zeta_tags($count);
		echo '</div>';
		echo '</div>';
		echo $after_widget;
      
		endif;

	}

	public function update( $new_instance, $old_instance ) {
		$instance = $old_instance;
		//Strip tags from title and name to remove HTML
		$instance['title']     = strip_tags(zeta_isset($new_instance,'title'));
		$instance['zt_font'] = strip_tags(zeta_isset($new_instance,'zt_font'));
		$instance['zt_count'] = strip_tags(zeta_isset($new_instance,'zt_count'));
		return $instance;
	}

	public function form($instance){
		//Set up some default widget settings.
		$defaults = array('title' => __z('Tags'), 'zt_font' => 'fixed', 'zt_count' => 'count');
		$instance = wp_parse_args( (array) $instance, $defaults ); ?>
		<p>
			<label for="<?php echo $this->get_field_id('title'); ?>"><?php _z('Title:'); ?></label>
			<input id="<?php echo $this->get_field_id('title'); ?>" name="<?php echo $this->get_field_name('title'); ?>" value="<?php echo $instance['title']; ?>" style="width:100%;" />
		</p>
		<p>
			<label for="<?php echo $this->get_field_id('zt_font'); ?>"><?php _z('Font Size'); ?></label>
			<select id="<?php echo $this->get_field_id('zt_font'); ?>" name="<?php echo $this->get_field_name('zt_font'); ?>" class="widefat" style="width:100%;">
				<option <?php if ('fixed' == $instance['zt_font'] ) echo 'selected="selected"'; ?> value="fixed"><?php _z('Fixed'); ?></option>
				<option <?php if ('dynamic' == $instance['zt_font'] ) echo 'selected="selected"'; ?> value="dynamic"><?php _z('Dynamic'); ?></option>
            </select>
		</p>
		<p>
			<input class="checkbox" type="checkbox" <?php checked( $instance[ 'zt_count' ], 'on'); ?> id="<?php echo $this->get_field_id('zt_count'); ?>" name="<?php echo $this->get_field_name('zt_count'); ?>" />
			<label for="<?php echo $this->get_field_id('zt_count'); ?>"> <?php _z('Display Count'); ?></label>
		</p>
	<?php
	}

}
