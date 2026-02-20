<?php
/**
 * @since 1.0.0
 * @version 2.0
 */
CSF::createSection(ZETA_OPTIONS,
    array(
        'id'    => 'homepage',
        'icon'  => 'fa fa-th-large',
        'title' => __z('Homepage')
    )
); 
 
CSF::createSection(ZETA_OPTIONS,
    array(
        'title'  => __z('Main Slider'),
        'parent' => 'homepage',
        'icon'   => 'fa fa-minus',
        'fields' => array(
            array(
                'id'      => 'slidershow',
                'type'    => 'switcher',
                'title'   => __z('Display Slider'),
                'subtitle'    => __z('Check whether to activate or deactivate'),
				'text_on'	=> __z('Enable'),
				'text_off'	=> __z('Disable'),
				'text_width' => 80,
                'default' => true
            ),
			array(
			  'id'       => 'sliderfilter',
			  'type'     => 'button_set',
			  'title'    => 'Filter Display',
			  'subtitle'    => __z('Select filter for slider contents'),
			  'multiple' => false,
			  'options'  => array(
				'slider'   => 'Slider',
				'genreid' => 'Genre',
				'postid' => 'Post',
				'Random' => 'Random',
				
			  ),
			  'default'  => 'slider'
			),

            array(
                'id'      => 'slideritems',
                'type'    => 'text',
                'title'   => __z('Items number'),
                'subtitle' => __z('Number of items to show'),
                'default' => '5',
                'attributes' => array(
                    'type' => 'number',
                    'style' => 'width:100px'
                )
            ),

            array(
                'id'       => 'sliderpostypes',
                'type'     => 'select',
                'title'    => __z('Post Types'),
                'subtitle' => __z('Select the type of content you want to display'),
                'default' => 'all',
                'options' => array(
                    'all'     => __z('Movies and TV Shows'),
                    'movies'  => __z('Only Movies'),
                    'tvshows' => __z('Only TV Shows')
                ),
            ),
			
            array(
                'id'      => 'slidermodorderby',
                'type'    => 'select',
                'title'   => __z('Order by'),
                'subtitle'    => __z('Order items for this module'),
                'default' => 'date',
                'options' => array(
                    'date'     => __z('Post date'),
                    'title'    => __z('Post title'),
                    'modified' => __z('Last modified'),
                    'rand'     => __z('Random entry')
                )
            ),
            array(
                'id'    => 'sliderodorder',
                'type'  => 'radio',
                'title' => __z('Order'),
                'options' => array(
                    'DESC' => __z('Descending'),
                    'ASC'  => __z('Ascending')
                ),
                'dependency' => array('slidermodorderby','!=','rand'),
                'default' => 'DESC'
            ),
            array(
                'type'    => 'notice',
                'style'   => 'warning',
                'content' => __z('If Full Width is enabled in customized settings, only fullscreen layout will be used for the slider.'),
            ),
            array(
                'id'    => 'sliderlayout',
                'type'  => 'radio',
                'title' => __z('Slider Layout'),
                'options' => array(
                    'fulls' => __z('Fullscreen'),
					'fullw' => __z('Full width'),
                    'fixw'  => __z('Fixed width'),
                ),
                'default' => 'fulls'
            ),
			
			
 
            array(
                'id'      => 'sliderpostids',
                'type'    => 'textarea',
                'title'    => __z('Posts ID'),
                'subtitle' => __z('Use the numeric IDs of the posts.'),
                'attributes' => array(
                    'placeholder' => '335,887,996,1085',
                    'rows' => '3'
                ),
                'after' => '<p>'.__z('Numeric IDs must be separated by a comma, use only numeric IDs of content that are established as Movies or TV Shows.').'</p>',
                'dependency' => array('sliderfilter','==', 'postid')
            ),
            array(
                'id'      => 'slidergenreids',
                'type'    => 'textarea',
                'title'    => __z('Genre ID'),
                'subtitle' => __z('Use the numeric IDs of the genre.'),
                'attributes' => array(
                    'placeholder' => '335,887,996,1085',
                    'rows' => '3'
                ),
                'after' => '<p>'.__z('Numeric IDs must be separated by a comma, use only numeric IDs of an existing genre').'</p>',
                'dependency' => array('sliderfilter','==', 'genreid')
            ),
            array(
                'id'    => 'slidercontrols',
                'type'  => 'checkbox',
                'title' => __z('Slider Options'),
                'subtitle'  => __z('Check to enable selected options.'),
                'options' => array(
                    'autoplay' 	 => __z('Autoplay'),
                    'hoverpause' => __z('Hover Pause'),
					'indicator'  => __z('Show Indicator')
                ),
                'dependency' => array('sliderlayout','!=', 'fulls'),
				'default' => array('autoplay','hoverpause')
            ),
            array(
                'id'    => 'sliderspeed',
                'type'  => 'select',
                'title' => __z('Speed Slider'),
                'subtitle'  => __z('Select speed slider in secons'),
                'options' => array(
                    '7000' => __z('7 seconds'),
                    '6500' => __z('6.5 seconds'),
                    '6000' => __z('6 seconds'),
                    '5500' => __z('5.5 seconds'),
                    '5000' => __z('5 seconds'),
                    '4500' => __z('4.5 seconds'),
                    '4000' => __z('4 seconds'),
                    '3500' => __z('3.5 seconds'),
                    '3000' => __z('3 seconds'),
                )
            )
        )
    )
);
