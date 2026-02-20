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
class ZT_Widget_blog_category extends WP_Widget {

	public function __construct() {
		$widget_ops = array('classname' => 'zetathemes_widget', 'description' => __z('Full list of blog categories') );
		$control_ops = array('width' => 300, 'height' => 350, 'id_base' => 'ztw_blogcat');
		parent::__construct('ztw_blogcat', __z('ZetaFlix - Blog Category'), $widget_ops, $control_ops );
	}

	public function widget( $args, $instance ) {
		extract( $args );
		$title = apply_filters('widget_title', zeta_isset($instance,'title') );
		$scroll = (isset($instance[ 'zt_count' ])) ? 'scrolling' : 'falsescroll';
		$blog = zeta_get_option('pageblog');
		
		// Widget
		
		echo $before_widget;
		
		if( is_page($blog) || is_singular('post') || is_admin() || is_tag() || is_category() ):
		
		if($title)
			
		echo $before_title . $title . $after_title;
		
		echo '<div class="sidebar-content">';
		echo '<div class="blog-cats">';
        echo '<ul class="cats-list">';
		zeta_li_categories();
        echo '</ul>';
        echo '</div>';
		echo '</div>';
				
		endif;
		
		
		echo $after_widget;

	}

	public function update( $new_instance, $old_instance ) {
		$instance = $old_instance;
		//Strip tags from title and name to remove HTML
		$instance['title']     = strip_tags(zeta_isset($new_instance,'title'));
		return $instance;
	}

	public function form($instance){
		//Set up some default widget settings.
		$defaults = array('title' => __z('Categories'), 'zt_count' => 'scrolling');
		$instance = wp_parse_args( (array) $instance, $defaults ); ?>
		<p>
			<label for="<?php echo $this->get_field_id('title'); ?>"><?php _z('Title:'); ?></label>
			<input id="<?php echo $this->get_field_id('title'); ?>" name="<?php echo $this->get_field_name('title'); ?>" value="<?php echo $instance['title']; ?>" style="width:100%;" />
		</p>
	<?php
	}

}
