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
class ZT_Widget_genres extends WP_Widget {

	public function __construct() {
		$widget_ops = array('classname' => 'zetathemes_widget', 'description' => __z('Full list of genres') );
		$control_ops = array('width' => 300, 'height' => 350, 'id_base' => 'ztw_mgenres');
		parent::__construct('ztw_mgenres', __z('ZetaFlix - Stream Genres List'), $widget_ops, $control_ops );
	}

	public function widget( $args, $instance ) {
		extract( $args );
		$title = apply_filters('widget_title',  zeta_isset($instance,'title')  );
		$scroll = $instance[ 'zt_scroll' ] ? 'scrolling' : 'falsescroll';
		// Widget
		
		echo $before_widget;
		
		if($title)
		echo $before_title . $title . $after_title;
		echo '<div class="sidebar-content">';
        echo '<div class="genre-listing">';
        echo '<ul class="genre-list '.$scroll.'">';
		zeta_li_genres();
        echo '</ul>';
        echo '</div>';
		echo '</div>';
		
		echo $after_widget;

	}

	public function update( $new_instance, $old_instance ) {
		$instance = $old_instance;
		//Strip tags from title and name to remove HTML
		$instance['title']     = strip_tags(zeta_isset($new_instance,'title'));
		$instance['zt_scroll'] = strip_tags(zeta_isset($new_instance,'zt_scroll'));
		return $instance;
	}

	public function form($instance){
		//Set up some default widget settings.
		$defaults = array('title' => __z('Genres'), 'zt_scroll' => 'scrolling');
		$instance = wp_parse_args( (array) $instance, $defaults ); ?>
		<p>
			<label for="<?php echo $this->get_field_id('title'); ?>"><?php _z('Title:'); ?></label>
			<input id="<?php echo $this->get_field_id('title'); ?>" name="<?php echo $this->get_field_name('title'); ?>" value="<?php echo $instance['title']; ?>" style="width:100%;" />
		</p>
		<p>
			<input class="checkbox" type="checkbox" <?php checked( $instance[ 'zt_scroll' ], 'on'); ?> id="<?php echo $this->get_field_id('zt_scroll'); ?>" name="<?php echo $this->get_field_name('zt_scroll'); ?>" />
			<label for="<?php echo $this->get_field_id('zt_scroll'); ?>"> <?php _z('Enable scrolling'); ?></label>
		</p>
	<?php
	}

}
